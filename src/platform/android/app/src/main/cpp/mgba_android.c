/* Copyright (c) 2026 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
#include <jni.h>

#include <android/log.h>
#include <fcntl.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <mgba/core/config.h>
#include <mgba/core/cheats.h>
#include <mgba/core/core.h>
#include <mgba/core/interface.h>
#include <mgba/core/log.h>
#include <mgba/core/rewind.h>
#include <mgba/core/serialize.h>
#include <mgba-util/audio-buffer.h>
#include <mgba-util/image.h>
#include <mgba-util/vfs.h>

struct AndroidCore {
	struct mCore* core;
	mColor* pixels;
	unsigned width;
	unsigned height;
	struct mCoreRewindContext rewind;
	struct mLogger logger;
	struct mLogFilter logFilter;
	FILE* logFile;
	char* cheatsPath;
};

static int _androidLogPriority(enum mLogLevel level) {
	switch (level) {
	case mLOG_FATAL:
		return ANDROID_LOG_FATAL;
	case mLOG_ERROR:
	case mLOG_GAME_ERROR:
		return ANDROID_LOG_ERROR;
	case mLOG_WARN:
		return ANDROID_LOG_WARN;
	case mLOG_INFO:
	case mLOG_STUB:
		return ANDROID_LOG_INFO;
	case mLOG_DEBUG:
	default:
		return ANDROID_LOG_DEBUG;
	}
}

static void _androidLog(
		struct mLogger* logger,
		int category,
		enum mLogLevel level,
		const char* format,
		va_list args) {
	struct AndroidCore* androidCore = (struct AndroidCore*) (
		(char*) logger - offsetof(struct AndroidCore, logger));
	char message[1024];
	vsnprintf(message, sizeof(message), format, args);
	const char* categoryName = mLogCategoryName(category);
	if (!categoryName) {
		categoryName = "mGBA";
	}
	__android_log_print(_androidLogPriority(level), "mGBA", "%s: %s", categoryName, message);
	if (androidCore->logFile) {
		fprintf(androidCore->logFile, "%s: %s\n", categoryName, message);
		fflush(androidCore->logFile);
	}
}

static void _initLogger(struct AndroidCore* androidCore) {
	mLogFilterInit(&androidCore->logFilter);
	androidCore->logFilter.defaultLevels = mLOG_FATAL | mLOG_ERROR | mLOG_WARN;
	androidCore->logger.log = _androidLog;
	androidCore->logger.filter = &androidCore->logFilter;
	mLogSetThreadLogger(&androidCore->logger);
}

static void _configureLogger(struct AndroidCore* androidCore) {
	if (androidCore->logFile) {
		fclose(androidCore->logFile);
		androidCore->logFile = NULL;
	}
	mLogFilterLoad(&androidCore->logFilter, &androidCore->core->config);
	bool logToFile = false;
	const char* path = mCoreConfigGetValue(&androidCore->core->config, "logFile");
	mCoreConfigGetBoolValue(&androidCore->core->config, "logToFile", &logToFile);
	if (logToFile && path && path[0]) {
		androidCore->logFile = fopen(path, "a");
	}
}

static void _deinitLogger(struct AndroidCore* androidCore) {
	if (androidCore->logFile) {
		fclose(androidCore->logFile);
		androidCore->logFile = NULL;
	}
	mLogSetThreadLogger(NULL);
	mLogFilterDeinit(&androidCore->logFilter);
}

static void _throw(JNIEnv* env, const char* className, const char* message) {
	jclass exceptionClass = (*env)->FindClass(env, className);
	if (exceptionClass) {
		(*env)->ThrowNew(env, exceptionClass, message);
	}
}

static struct AndroidCore* _fromHandle(jlong handle) {
	return (struct AndroidCore*) (uintptr_t) handle;
}

static void _autosaveCheats(struct AndroidCore* androidCore) {
	if (!androidCore->core || !androidCore->cheatsPath) {
		return;
	}
	bool autosave = true;
	mCoreConfigGetBoolValue(&androidCore->core->config, "cheatAutosave", &autosave);
	if (!autosave) {
		return;
	}
	struct VFile* cheats = VFileOpen(androidCore->cheatsPath, O_CREAT | O_TRUNC | O_RDWR);
	if (cheats) {
		mCheatSaveFile(androidCore->core->cheatDevice(androidCore->core), cheats);
		cheats->close(cheats);
	}
}

static void _closeCore(struct AndroidCore* androidCore) {
	if (!androidCore) {
		return;
	}
	mCoreRewindContextDeinit(&androidCore->rewind);
	if (androidCore->core) {
		_autosaveCheats(androidCore);
		androidCore->core->unloadROM(androidCore->core);
		mCoreConfigDeinit(&androidCore->core->config);
		androidCore->core->deinit(androidCore->core);
	}
	_deinitLogger(androidCore);
	free(androidCore->cheatsPath);
	free(androidCore->pixels);
	free(androidCore);
}

static bool _setOptions(
		JNIEnv* env,
		struct mCore* core,
		jobjectArray options,
		bool initial) {
	if (!options) {
		return true;
	}
	jsize optionCount = (*env)->GetArrayLength(env, options);
	if (optionCount % 2) {
		_throw(env, "java/lang/IllegalArgumentException", "Core options must be key/value pairs");
		return false;
	}

	for (jsize index = 0; index < optionCount; index += 2) {
		jstring keyString = (jstring) (*env)->GetObjectArrayElement(env, options, index);
		jstring valueString = (jstring) (*env)->GetObjectArrayElement(env, options, index + 1);
		const char* key = (*env)->GetStringUTFChars(env, keyString, NULL);
		const char* value = (*env)->GetStringUTFChars(env, valueString, NULL);
		if (!key || !value) {
			if (key) {
				(*env)->ReleaseStringUTFChars(env, keyString, key);
			}
			if (value) {
				(*env)->ReleaseStringUTFChars(env, valueString, value);
			}
			(*env)->DeleteLocalRef(env, keyString);
			(*env)->DeleteLocalRef(env, valueString);
			return false;
		}

		mCoreConfigSetValue(&core->config, key, value);
		if (!initial) {
			core->reloadConfigOption(core, key, &core->config);
		}
		(*env)->ReleaseStringUTFChars(env, keyString, key);
		(*env)->ReleaseStringUTFChars(env, valueString, value);
		(*env)->DeleteLocalRef(env, keyString);
		(*env)->DeleteLocalRef(env, valueString);
	}

	if (initial) {
		mCoreConfigMap(&core->config, &core->opts);
	}
	core->loadConfig(core, &core->config);
	return true;
}

static bool _loadPatch(struct mCore* core, const char* path) {
	if (!path || !path[0]) {
		return true;
	}
	struct VFile* patch = VFileOpen(path, O_RDONLY);
	if (!patch) {
		return false;
	}
	bool loaded = core->loadPatch(core, patch);
	patch->close(patch);
	return loaded;
}

static bool _loadCheats(struct mCore* core, const char* path) {
	if (!path || !path[0]) {
		return true;
	}
	struct VFile* cheats = VFileOpen(path, O_RDONLY);
	if (!cheats) {
		return false;
	}
	bool loaded = mCheatParseFile(core->cheatDevice(core), cheats);
	cheats->close(cheats);
	return loaded;
}

JNIEXPORT jlong JNICALL
Java_io_mgba_android_core_MgbaNativeCore_nativeOpen(
		JNIEnv* env,
		jobject self,
		jstring romPath,
		jstring savePath,
		jobjectArray options,
		jboolean preloadRom,
		jstring patchPath,
		jstring cheatsPath) {
	(void) self;
	if (!romPath || !savePath) {
		_throw(env, "java/lang/IllegalArgumentException", "ROM and save paths are required");
		return 0;
	}

	const char* rom = (*env)->GetStringUTFChars(env, romPath, NULL);
	const char* save = (*env)->GetStringUTFChars(env, savePath, NULL);
	if (!rom || !save) {
		if (rom) {
			(*env)->ReleaseStringUTFChars(env, romPath, rom);
		}
		if (save) {
			(*env)->ReleaseStringUTFChars(env, savePath, save);
		}
		return 0;
	}

	struct AndroidCore* androidCore = calloc(1, sizeof(*androidCore));
	if (!androidCore) {
		(*env)->ReleaseStringUTFChars(env, romPath, rom);
		(*env)->ReleaseStringUTFChars(env, savePath, save);
		_throw(env, "java/lang/OutOfMemoryError", "Could not allocate the emulator context");
		return 0;
	}
	_initLogger(androidCore);

	androidCore->core = mCoreFind(rom);
	if (!androidCore->core || !androidCore->core->init(androidCore->core)) {
		(*env)->ReleaseStringUTFChars(env, romPath, rom);
		(*env)->ReleaseStringUTFChars(env, savePath, save);
		if (androidCore->core) {
			androidCore->core->deinit(androidCore->core);
			androidCore->core = NULL;
		}
		_deinitLogger(androidCore);
		free(androidCore);
		_throw(env, "java/lang/IllegalArgumentException", "The selected file is not a supported GBA ROM");
		return 0;
	}

	mCoreInitConfig(androidCore->core, "android");
	mCoreConfigSetDefaultValue(&androidCore->core->config, "idleOptimization", "detect");
	mCoreConfigSetDefaultIntValue(&androidCore->core->config, "audioBuffers", 1536);
	mCoreConfigSetDefaultIntValue(&androidCore->core->config, "sampleRate", 44100);
	mCoreConfigSetDefaultIntValue(&androidCore->core->config, "volume", 0x100);
	if (!_setOptions(env, androidCore->core, options, true)) {
		(*env)->ReleaseStringUTFChars(env, romPath, rom);
		(*env)->ReleaseStringUTFChars(env, savePath, save);
		_closeCore(androidCore);
		return 0;
	}
	_configureLogger(androidCore);

	androidCore->core->baseVideoSize(
		androidCore->core,
		&androidCore->width,
		&androidCore->height);
	androidCore->pixels = calloc(
		(size_t) androidCore->width * androidCore->height,
		sizeof(*androidCore->pixels));
	if (!androidCore->pixels) {
		(*env)->ReleaseStringUTFChars(env, romPath, rom);
		(*env)->ReleaseStringUTFChars(env, savePath, save);
		_closeCore(androidCore);
		_throw(env, "java/lang/OutOfMemoryError", "Could not allocate the video buffer");
		return 0;
	}
	androidCore->core->setVideoBuffer(
		androidCore->core,
		androidCore->pixels,
		androidCore->width);

	bool loaded = preloadRom
		? mCorePreloadFile(androidCore->core, rom)
		: mCoreLoadFile(androidCore->core, rom);
	const char* patch = patchPath ? (*env)->GetStringUTFChars(env, patchPath, NULL) : NULL;
	const char* cheats = cheatsPath ? (*env)->GetStringUTFChars(env, cheatsPath, NULL) : NULL;
	bool patchLoaded = loaded && _loadPatch(androidCore->core, patch);
	bool cheatsLoaded = patchLoaded && _loadCheats(androidCore->core, cheats);
	if (cheatsLoaded && cheats && cheats[0]) {
		androidCore->cheatsPath = strdup(cheats);
	}
	bool saveLoaded = loaded && mCoreLoadSaveFile(androidCore->core, save, false);
	if (patch) {
		(*env)->ReleaseStringUTFChars(env, patchPath, patch);
	}
	if (cheats) {
		(*env)->ReleaseStringUTFChars(env, cheatsPath, cheats);
	}
	(*env)->ReleaseStringUTFChars(env, romPath, rom);
	(*env)->ReleaseStringUTFChars(env, savePath, save);

	if (!loaded || !patchLoaded || !cheatsLoaded || !saveLoaded) {
		_closeCore(androidCore);
		_throw(
			env,
			"java/lang/IllegalStateException",
			!loaded ? "mGBA could not load the selected ROM" :
			!patchLoaded ? "mGBA could not load the selected patch" :
			!cheatsLoaded ? "mGBA could not load the selected cheat file" :
			"Could not open the save file");
		return 0;
	}

	androidCore->core->setAudioBufferSize(
		androidCore->core,
		androidCore->core->opts.audioBuffers ? androidCore->core->opts.audioBuffers : 1536);
	androidCore->core->reset(androidCore->core);
	return (jlong) (uintptr_t) androidCore;
}

JNIEXPORT void JNICALL
Java_io_mgba_android_core_MgbaNativeCore_nativeApplyConfig(
		JNIEnv* env,
		jobject self,
		jlong handle,
		jobjectArray options) {
	(void) self;
	struct AndroidCore* androidCore = _fromHandle(handle);
	if (!androidCore) {
		return;
	}
	if (_setOptions(env, androidCore->core, options, false)) {
		_configureLogger(androidCore);
	}
}

JNIEXPORT jstring JNICALL
Java_io_mgba_android_core_MgbaNativeCore_nativeTitle(
		JNIEnv* env,
		jobject self,
		jlong handle) {
	(void) self;
	struct AndroidCore* androidCore = _fromHandle(handle);
	if (!androidCore) {
		_throw(env, "java/lang/IllegalStateException", "The emulator core is not loaded");
		return NULL;
	}
	struct mGameInfo info = { 0 };
	androidCore->core->getGameInfo(androidCore->core, &info);
	return (*env)->NewStringUTF(env, info.title[0] ? info.title : "Game Boy Advance");
}

JNIEXPORT jint JNICALL
Java_io_mgba_android_core_MgbaNativeCore_nativeWidth(
		JNIEnv* env,
		jobject self,
		jlong handle) {
	(void) env;
	(void) self;
	struct AndroidCore* androidCore = _fromHandle(handle);
	return androidCore ? (jint) androidCore->width : 0;
}

JNIEXPORT jint JNICALL
Java_io_mgba_android_core_MgbaNativeCore_nativeHeight(
		JNIEnv* env,
		jobject self,
		jlong handle) {
	(void) env;
	(void) self;
	struct AndroidCore* androidCore = _fromHandle(handle);
	return androidCore ? (jint) androidCore->height : 0;
}

JNIEXPORT jint JNICALL
Java_io_mgba_android_core_MgbaNativeCore_nativeAudioSampleRate(
		JNIEnv* env,
		jobject self,
		jlong handle) {
	(void) env;
	(void) self;
	struct AndroidCore* androidCore = _fromHandle(handle);
	return androidCore ? (jint) androidCore->core->audioSampleRate(androidCore->core) : 0;
}

JNIEXPORT void JNICALL
Java_io_mgba_android_core_MgbaNativeCore_nativeRunFrame(
		JNIEnv* env,
		jobject self,
		jlong handle,
		jintArray output) {
	(void) self;
	struct AndroidCore* androidCore = _fromHandle(handle);
	if (!androidCore || !output) {
		_throw(env, "java/lang/IllegalStateException", "The emulator core is not loaded");
		return;
	}

	jsize pixelCount = (jsize) (androidCore->width * androidCore->height);
	if ((*env)->GetArrayLength(env, output) < pixelCount) {
		_throw(env, "java/lang/IllegalArgumentException", "The frame buffer is too small");
		return;
	}

	androidCore->core->runFrame(androidCore->core);
	jint* destination = (*env)->GetPrimitiveArrayCritical(env, output, NULL);
	if (!destination) {
		return;
	}
	for (jsize index = 0; index < pixelCount; ++index) {
		uint32_t color = androidCore->pixels[index];
		destination[index] = (jint) (
			0xFF000000U |
			((color & 0x000000FFU) << 16) |
			(color & 0x0000FF00U) |
			((color & 0x00FF0000U) >> 16));
	}
	(*env)->ReleasePrimitiveArrayCritical(env, output, destination, 0);
}

JNIEXPORT jint JNICALL
Java_io_mgba_android_core_MgbaNativeCore_nativeReadAudio(
		JNIEnv* env,
		jobject self,
		jlong handle,
		jshortArray output) {
	(void) self;
	struct AndroidCore* androidCore = _fromHandle(handle);
	if (!androidCore || !output) {
		return 0;
	}

	struct mAudioBuffer* audio = androidCore->core->getAudioBuffer(androidCore->core);
	size_t available = mAudioBufferAvailable(audio);
	size_t capacity = (size_t) (*env)->GetArrayLength(env, output) / 2;
	if (available > capacity) {
		available = capacity;
	}
	if (!available) {
		return 0;
	}

	jshort* destination = (*env)->GetPrimitiveArrayCritical(env, output, NULL);
	if (!destination) {
		return 0;
	}
	size_t read = mAudioBufferRead(audio, (int16_t*) destination, available);
	(*env)->ReleasePrimitiveArrayCritical(env, output, destination, 0);
	return (jint) read;
}

JNIEXPORT void JNICALL
Java_io_mgba_android_core_MgbaNativeCore_nativeSetKey(
		JNIEnv* env,
		jobject self,
		jlong handle,
		jint key,
		jboolean pressed) {
	(void) self;
	struct AndroidCore* androidCore = _fromHandle(handle);
	if (!androidCore) {
		return;
	}
	if (key < 0 || key > 9) {
		_throw(env, "java/lang/IllegalArgumentException", "Unknown GBA key");
		return;
	}
	uint32_t mask = 1U << key;
	if (pressed) {
		androidCore->core->addKeys(androidCore->core, mask);
	} else {
		androidCore->core->clearKeys(androidCore->core, mask);
	}
}

JNIEXPORT void JNICALL
Java_io_mgba_android_core_MgbaNativeCore_nativeClearKeys(
		JNIEnv* env,
		jobject self,
		jlong handle) {
	(void) env;
	(void) self;
	struct AndroidCore* androidCore = _fromHandle(handle);
	if (androidCore) {
		androidCore->core->setKeys(androidCore->core, 0);
	}
}

JNIEXPORT void JNICALL
Java_io_mgba_android_core_MgbaNativeCore_nativeConfigureRewind(
		JNIEnv* env,
		jobject self,
		jlong handle,
		jboolean enabled,
		jint capacity) {
	(void) env;
	(void) self;
	struct AndroidCore* androidCore = _fromHandle(handle);
	if (!androidCore) {
		return;
	}
	mCoreRewindContextDeinit(&androidCore->rewind);
	if (enabled && capacity > 0) {
		mCoreRewindContextInit(&androidCore->rewind, (size_t) capacity, false);
	}
}

JNIEXPORT void JNICALL
Java_io_mgba_android_core_MgbaNativeCore_nativeCaptureRewind(
		JNIEnv* env,
		jobject self,
		jlong handle) {
	(void) env;
	(void) self;
	struct AndroidCore* androidCore = _fromHandle(handle);
	if (androidCore && androidCore->rewind.currentState) {
		mCoreRewindAppend(&androidCore->rewind, androidCore->core);
	}
}

JNIEXPORT jboolean JNICALL
Java_io_mgba_android_core_MgbaNativeCore_nativeRewind(
		JNIEnv* env,
		jobject self,
		jlong handle) {
	(void) env;
	(void) self;
	struct AndroidCore* androidCore = _fromHandle(handle);
	return androidCore && androidCore->rewind.currentState &&
		mCoreRewindRestore(&androidCore->rewind, androidCore->core, 1);
}

JNIEXPORT jbyteArray JNICALL
Java_io_mgba_android_core_MgbaNativeCore_nativeSaveState(
		JNIEnv* env,
		jobject self,
		jlong handle) {
	(void) self;
	struct AndroidCore* androidCore = _fromHandle(handle);
	if (!androidCore) {
		return NULL;
	}
	size_t size = androidCore->core->stateSize(androidCore->core);
	void* state = malloc(size);
	if (!state) {
		_throw(env, "java/lang/OutOfMemoryError", "Could not allocate state buffer");
		return NULL;
	}
	if (!androidCore->core->saveState(androidCore->core, state)) {
		free(state);
		return NULL;
	}
	jbyteArray result = (*env)->NewByteArray(env, (jsize) size);
	if (result) {
		(*env)->SetByteArrayRegion(env, result, 0, (jsize) size, state);
	}
	free(state);
	return result;
}

JNIEXPORT jboolean JNICALL
Java_io_mgba_android_core_MgbaNativeCore_nativeLoadState(
		JNIEnv* env,
		jobject self,
		jlong handle,
		jbyteArray input) {
	(void) self;
	struct AndroidCore* androidCore = _fromHandle(handle);
	if (!androidCore || !input) {
		return false;
	}
	size_t size = androidCore->core->stateSize(androidCore->core);
	if ((size_t) (*env)->GetArrayLength(env, input) != size) {
		return false;
	}
	void* state = malloc(size);
	if (!state) {
		return false;
	}
	(*env)->GetByteArrayRegion(env, input, 0, (jsize) size, state);
	bool loaded = androidCore->core->loadState(androidCore->core, state);
	free(state);
	return loaded;
}

JNIEXPORT jboolean JNICALL
Java_io_mgba_android_core_MgbaNativeCore_nativeSaveStateFile(
		JNIEnv* env,
		jobject self,
		jlong handle,
		jstring pathString,
		jint flags) {
	(void) self;
	struct AndroidCore* androidCore = _fromHandle(handle);
	if (!androidCore || !pathString) {
		return false;
	}
	const char* path = (*env)->GetStringUTFChars(env, pathString, NULL);
	struct VFile* file = path ? VFileOpen(path, O_CREAT | O_TRUNC | O_RDWR) : NULL;
	bool saved = file && mCoreSaveStateNamed(androidCore->core, file, flags);
	if (file) {
		file->close(file);
	}
	if (path) {
		(*env)->ReleaseStringUTFChars(env, pathString, path);
	}
	return saved;
}

JNIEXPORT jboolean JNICALL
Java_io_mgba_android_core_MgbaNativeCore_nativeLoadStateFile(
		JNIEnv* env,
		jobject self,
		jlong handle,
		jstring pathString,
		jint flags) {
	(void) self;
	struct AndroidCore* androidCore = _fromHandle(handle);
	if (!androidCore || !pathString) {
		return false;
	}
	const char* path = (*env)->GetStringUTFChars(env, pathString, NULL);
	struct VFile* file = path ? VFileOpen(path, O_RDONLY) : NULL;
	bool loaded = file && mCoreLoadStateNamed(androidCore->core, file, flags);
	if (file) {
		file->close(file);
	}
	if (path) {
		(*env)->ReleaseStringUTFChars(env, pathString, path);
	}
	return loaded;
}

JNIEXPORT void JNICALL
Java_io_mgba_android_core_MgbaNativeCore_nativeReset(
		JNIEnv* env,
		jobject self,
		jlong handle) {
	(void) env;
	(void) self;
	struct AndroidCore* androidCore = _fromHandle(handle);
	if (androidCore) {
		androidCore->core->reset(androidCore->core);
	}
}

JNIEXPORT void JNICALL
Java_io_mgba_android_core_MgbaNativeCore_nativeClose(
		JNIEnv* env,
		jobject self,
		jlong handle) {
	(void) env;
	(void) self;
	_closeCore(_fromHandle(handle));
}

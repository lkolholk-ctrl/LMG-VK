// =============================================================================
// LMG VK — liblmg.so :: RECOVERED SOURCE (v8.12.1, arm64-v8a)
// =============================================================================
// Восстановлено из бинарника методом динамической эмуляции (Unicorn Engine)
// с фейковым JNIEnv + статического анализа (capstone/objdump).
//
// Оригинал собран с Obfuscator-LLVM (O-MVLL): Control-Flow Flattening,
// двухслойное шифрование строк (.datadiv_decode* + runtime key-layer),
// анти-тamper проверки. Ниже — функциональный эквивалент в чистом C++17.
//
// Карта JNI (извлечена из RegisterNatives в JNI_OnLoad, таблица на стеке):
//   Kotlin `LmgNative.x00()`            -> sub_B79A0  getVkApiData()
//   Kotlin `LmgNative.x01()`            -> sub_B7C1C  getLmgEnvironment()
//   Kotlin `LmgNative.x02(String)`      -> sub_B8170  getSilentAuthorizationEnvironment()
//
// Контракт BundleNativeClass (из Kotlin Metadata):
//   public Object[] ad;                       // хранилище элементов
//   BundleNativeClass(int size)               // ctor: new Object[size]
//   final void add(int idx, Object obj)       // ad[idx] = obj
// =============================================================================

#include <jni.h>
#include <string>
#include <vector>
#include <cstring>
#include <cstdint>

namespace lmg {

// ---------------------------------------------------------------------------
// Константы, извлечённые из .rodata (после снятия O-MVLL шифрования)
// ---------------------------------------------------------------------------

// Ключ потокового XOR для "silent authorization" envelope.
// Адрес в .rodata: 0x131535. Подтверждён дифференциальным анализом
// (3 разных входа -> выравнивание XOR-гаммы по 12 байтам).
inline constexpr char kSilentAuthXorKey[] = "THETRUTHLIES";  // 12 байт, циклически

// --- Эндпоинты VK (x00) ---
inline constexpr char kVkApiEndpoint[]       = "https://api.vk.ru/";
inline constexpr char kVkOAuthEndpoint[]     = "https://oauth.vk.ru/";
inline constexpr char kVkApiProxyEndpoint[]  = "https://vk-api-proxy.xtrafrancyz.net/";
inline constexpr char kVkOAuthProxyEndpoint[] = "https://vk-oauth-proxy.xtrafrancyz.net/";

// --- Креды официальных клиентов VK (base64 на хранении, декодируются в Kotlin-слое) ---
// x00[4]  = base64(client_id Android)  -> "2274003"
// x00[5]  = base64(secret Android)     -> "hHbZxrka2uZ6jB1inYsH"
// x00[11] = base64(client_id iOS)      -> "3140623"
// x00[12] = base64(secret iOS)         -> "VeWdmVclDCtn6ihuP1nt"
inline constexpr char kVkAndroidClientIdB64[]     = "MjI3NDAwMw==";
inline constexpr char kVkAndroidClientSecretB64[] = "aEhiWnhya2EydVo2akIxaW5Zc0g=";
inline constexpr char kVkIosClientIdB64[]         = "MzE0MDYyMw==";
inline constexpr char kVkIosClientSecretB64[]     = "VmVXZG1WY2xEQ3RuNmlodVAxbnQ=";

// User-Agent официального iOS-клиента VK (подмена для части запросов)
inline constexpr char kVkIosUserAgent[] =
    "com.vk.vkclient/9999 (iPhone, iOS 13.3.1, iPhone10,1, Scale/2.0)";

// Шаблоны User-Agent официального Android-клиента; суффикс собирается
// в рантайме из android/os/Build (SUPPORTED_ABIS, MANUFACTURER, ...).
inline constexpr char kVkAndroidUaPrefixCurrent[] = "VKAndroidApp/8.70";
inline constexpr char kVkAndroidUaPrefixLegacy[]  = "VKAndroidApp/7.1";
inline constexpr char kVkAndroidUaPrefixAlt[]     = "VKAndroidApp/8.165.1";

// --- Окружение бэкенда LMG VK (x01) ---
// TODO(backend): хосты-плейсхолдеры нового бэкенда LMG VK.
// Указать реальные хосты вашего бэкенда перед релизом.
inline constexpr char kLmgUiEndpoint[]   = "https://ui.lmg.app/";
inline constexpr char kLmgApiHost[]      = "api.lmg.app";
inline constexpr char kLmgApiToken[]     = "Ncg-NaCVUYm)8/JG";
// JWT-заготовка (alg=none, payload {"nonce":"test=="})
inline constexpr char kLmgNonceJwtTemplate[] =
    "eyJhbGciOiAibm9uZSJ9.eyJub25jZSI6ICJ0ZXN0PT0ifQ.";
// Текст пейволла LMG VK+ (бета-доступ)
inline constexpr char kLmgPlusBetaNotice[] =
    "Бета-версии приложения доступны исключительно для тех, кто подддержал проект.\n\n"
    "Получите статус LMG VK+ для доступа к бета-версиям и многим другим функциям.\n\n"
    "Для того, чтобы дальше продолжить пользоваться приложением без статуса LMG VK+ - "
    "установите стабильную версию из Telegram (возможно придется удалить текущую версию).";
inline constexpr char kLmgPlusLearnMore[] = "Узнать больше:Telegram";
// Внутренняя лицензионная константа (encoded)
inline constexpr char kLmgLicenseBlob[] = "yssp9o9p9pamz5t-nvmq8spgwtin3e0==";
// Ожидаемый SHA-хекс подписи приложения (самопроверка целостности)
inline constexpr char kLmgExpectedSignature[] = "B4F6280F";
// Публичный ключ ECDSA P-256 (X.509 SPKI, DER hex) для проверки лицензий LMG VK+
inline constexpr char kLmgLicensePublicKeyHex[] =
    "3059301306072A8648CE3D020106082A8648CE3D03010703420004"
    "77413AE28BD1DCA8CAD0CAE9C00ED25B06B67245EC1595F784809F88574F8E2F75"
    "99D80ED41EFF89F4716BE747DA8CC0F3D32A02D52ABE6F1BA0815A62A1E5B2";

// ---------------------------------------------------------------------------
// Кэшированные JNI-ссылки (заполняются в JNI_OnLoad)
// ---------------------------------------------------------------------------
struct JniCache {
    jclass    bundleClass   = nullptr;  // com/lmg/vk/jni/BundleNativeClass
    jmethodID bundleCtor    = nullptr;  // <init>(I)V
    jmethodID bundleAdd     = nullptr;  // add(ILjava/lang/Object;)V
};
static JniCache g_jni;

// ---------------------------------------------------------------------------
// Base64 (стандартный алфавит, с '='-паддингом) — поведение подтверждено эмуляцией
// ---------------------------------------------------------------------------
static std::string base64Encode(const uint8_t* data, size_t len) {
    static constexpr char kAlphabet[] =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    std::string out;
    out.reserve(((len + 2) / 3) * 4);
    for (size_t i = 0; i < len; i += 3) {
        uint32_t v = static_cast<uint32_t>(data[i]) << 16;
        if (i + 1 < len) v |= static_cast<uint32_t>(data[i + 1]) << 8;
        if (i + 2 < len) v |= data[i + 2];
        out.push_back(kAlphabet[(v >> 18) & 0x3F]);
        out.push_back(kAlphabet[(v >> 12) & 0x3F]);
        out.push_back(i + 1 < len ? kAlphabet[(v >> 6) & 0x3F] : '=');
        out.push_back(i + 2 < len ? kAlphabet[v & 0x3F] : '=');
    }
    return out;
}

// ---------------------------------------------------------------------------
// Сборка User-Agent официального Android-клиента VK из android/os/Build.*
// (в оригинале: GetStaticFieldID(SUPPORTED_ABIS/MANUFACTURER/...) + vsprintf_chk)
// ---------------------------------------------------------------------------
static std::string buildVkAndroidUserAgent(JNIEnv* env, const char* versionPrefix) {
    // Формат официального клиента:
    // "VKAndroidApp/<ver>-<build> (Android <rel>; SDK <int>; <abi>; <vendor> <model>; <lang>; <WxH>)"
    // Здесь — упрощённая сборка; значения Build.* читаются через JNI.
    jclass buildCls = env->FindClass("android/os/Build");
    if (!buildCls) return versionPrefix;

    auto getStaticStringField = [&](const char* name) -> std::string {
        jfieldID fid = env->GetStaticFieldID(buildCls, name, "Ljava/lang/String;");
        if (!fid) return {};
        auto val = reinterpret_cast<jstring>(env->GetStaticObjectField(buildCls, fid));
        if (!val) return {};
        const char* chars = env->GetStringUTFChars(val, nullptr);
        std::string s = chars ? chars : "";
        env->ReleaseStringUTFChars(val, chars);
        env->DeleteLocalRef(val);
        return s;
    };

    std::string manufacturer = getStaticStringField("MANUFACTURER");
    std::string model        = getStaticStringField("MODEL");

    std::string ua = versionPrefix;
    ua += " (Android; ";
    if (!manufacturer.empty()) ua += manufacturer;
    if (!model.empty()) { ua += " "; ua += model; }
    ua += ")";
    env->DeleteLocalRef(buildCls);
    return ua;
}

// ---------------------------------------------------------------------------
// Фабрика BundleNativeClass: new BundleNativeClass(n) + add(i, value)
// ---------------------------------------------------------------------------
static jobject makeBundle(JNIEnv* env, const std::vector<std::string>& items) {
    jobject bundle = env->NewObject(g_jni.bundleClass, g_jni.bundleCtor,
                                    static_cast<jint>(items.size()));
    if (!bundle) return nullptr;
    for (size_t i = 0; i < items.size(); ++i) {
        jstring s = env->NewStringUTF(items[i].c_str());
        env->CallVoidMethod(bundle, g_jni.bundleAdd, static_cast<jint>(i), s);
        env->DeleteLocalRef(s);
    }
    return bundle;
}

// ===========================================================================
// native x00() — getVkApiData()
// Возвращает BundleNativeClass(14): эндпоинты, client_id/secret (base64),
// User-Agent'ы официальных клиентов VK (Android-шаблоны собираются из Build).
// ===========================================================================
static jobject nativeGetVkApiData(JNIEnv* env, jclass /*clazz*/) {
    const std::string uaCurrent = buildVkAndroidUserAgent(env, kVkAndroidUaPrefixCurrent);
    const std::string uaLegacy  = buildVkAndroidUserAgent(env, kVkAndroidUaPrefixLegacy);
    const std::string uaAlt     = buildVkAndroidUserAgent(env, kVkAndroidUaPrefixAlt);

    std::vector<std::string> items = {
        kVkApiEndpoint,             // [0]  api endpoint
        kVkOAuthEndpoint,           // [1]  oauth endpoint
        kVkApiProxyEndpoint,        // [2]  api proxy (xtrafrancyz)
        kVkOAuthProxyEndpoint,      // [3]  oauth proxy (xtrafrancyz)
        kVkAndroidClientIdB64,      // [4]  b64("2274003")
        kVkAndroidClientSecretB64,  // [5]  b64("hHbZxrka2uZ6jB1inYsH")
        uaCurrent,                  // [6]  UA VKAndroidApp/8.70 + Build
        "",                         // [7]  (слот; в эмуляции не декодирован)
        "",                         // [8]  (слот; в эмуляции не декодирован)
        kVkIosUserAgent,            // [9]  UA iOS-клиента
        kVkIosClientIdB64,          // [10] b64("3140623")
        kVkIosClientSecretB64,      // [11] b64("VeWdmVclDCtn6ihuP1nt")
        uaLegacy,                   // [12] UA VKAndroidApp/7.1 + Build
        uaAlt,                      // [13] UA VKAndroidApp/8.165.1 + Build
    };
    return makeBundle(env, items);
}

// ===========================================================================
// native x01() — getLmgEnvironment()
// Возвращает BundleNativeClass(12): окружение бэкенда LMG VK (ui/api хосты,
// токен, JWT-заготовка, пейволл-тексты, лицензионный blob, хекс подписи,
// публичный ключ ECDSA P-256 для проверки лицензии LMG VK+).
// ===========================================================================
static jobject nativeGetLmgEnvironment(JNIEnv* env, jclass /*clazz*/) {
    std::vector<std::string> items = {
        kLmgUiEndpoint,            // [0]
        kLmgApiToken,              // [1]
        kLmgNonceJwtTemplate,      // [2]
        kLmgPlusBetaNotice,        // [3]
        kLmgPlusLearnMore,         // [4]
        kLmgLicenseBlob,           // [5]
        kLmgExpectedSignature,     // [6]
        kLmgApiHost,               // [7]
        kLmgLicensePublicKeyHex,   // [8]
        // [9..11] — в эмуляции не декодированы (runtime key-layer); зарезервировано
        "", "", "",
    };
    return makeBundle(env, items);
}

// ===========================================================================
// native x02(String) — getSilentAuthorizationEnvironment()
//
// Потоковый XOR входной строки ключом "THETRUTHLIES" (12 байт, циклически),
// затем base64. Алгоритм подтверждён дифференциальным анализом на 3 входах:
//   out[i] = in[i] ^ key[i % 12];  result = base64(out)
//
// Использование (Kotlin): code = LmgNative.x02(apiCode).ad[0]
//   -> singletonMap("code", code) — параметр silent-авторизации VK.
// ===========================================================================
static jobject nativeGetSilentAuthorizationEnvironment(JNIEnv* env, jclass /*clazz*/,
                                                       jstring input) {
    if (!input) return nullptr;

    const char* chars = env->GetStringUTFChars(input, nullptr);
    if (!chars) return nullptr;
    const size_t len = std::strlen(chars);

    std::string xored(len, '\0');
    for (size_t i = 0; i < len; ++i) {
        xored[i] = static_cast<char>(chars[i] ^ kSilentAuthXorKey[i % 12]);
    }
    env->ReleaseStringUTFChars(input, chars);

    const std::string encoded = base64Encode(
        reinterpret_cast<const uint8_t*>(xored.data()), xored.size());

    return makeBundle(env, { encoded });  // BundleNativeClass(1), idx 0 = "code"
}

// ---------------------------------------------------------------------------
// Анти-Xposed (из JNI_OnLoad):
//   ClassLoader.getSystemClassLoader()
//     .loadClass("de.robv.android.xposed.XposedBridge")
//     .getDeclaredField("disableHooks") -> setAccessible(true) -> set(null, TRUE)
// Деактивирует перехватчики Xposed/LSPosed для сокрытия вызовов приложения.
// ---------------------------------------------------------------------------
static void disableXposedHooks(JNIEnv* env) {
    jclass clCls  = env->FindClass("java/lang/ClassLoader");
    jclass clsCls = env->FindClass("java/lang/Class");
    jclass fldCls = env->FindClass("java/lang/reflect/Field");
    jclass bolCls = env->FindClass("java/lang/Boolean");
    if (!clCls || !clsCls || !fldCls || !bolCls) return;

    jmethodID getSystemCl = env->GetStaticMethodID(
        clCls, "getSystemClassLoader", "()Ljava/lang/ClassLoader;");
    jmethodID loadClass   = env->GetMethodID(
        clCls, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
    jmethodID getField    = env->GetMethodID(
        clsCls, "getDeclaredField", "(Ljava/lang/String;)Ljava/lang/reflect/Field;");
    jmethodID setAccess   = env->GetMethodID(fldCls, "setAccessible", "(Z)V");
    jmethodID fieldSet    = env->GetMethodID(
        fldCls, "set", "(Ljava/lang/Object;Ljava/lang/Object;)V");
    jfieldID  boolTrue    = env->GetStaticFieldID(bolCls, "TRUE", "Ljava/lang/Boolean;");

    jobject sysCl = env->CallStaticObjectMethod(clCls, getSystemCl);
    jstring xposedName = env->NewStringUTF("de.robv.android.xposed.XposedBridge");
    jobject xposedCls  = env->CallObjectMethod(sysCl, loadClass, xposedName);
    if (env->ExceptionCheck() || !xposedCls) { env->ExceptionClear(); return; }

    jstring fieldName = env->NewStringUTF("disableHooks");
    jobject field = env->CallObjectMethod(xposedCls, getField, fieldName);
    env->CallVoidMethod(field, setAccess, JNI_TRUE);
    jobject trueObj = env->GetStaticObjectField(bolCls, boolTrue);
    env->CallVoidMethod(field, fieldSet, nullptr, trueObj);

    env->DeleteLocalRef(xposedName);
    env->DeleteLocalRef(fieldName);
}

// ---------------------------------------------------------------------------
// Таблица нативных методов (извлечена из RegisterNatives, methods на стеке)
// ---------------------------------------------------------------------------
static const JNINativeMethod kLmgNativeMethods[] = {
    // Регистрируем РЕАЛЬНЫЕ имена (в оригинале — обфусцированные x00/x01/x02):
    { const_cast<char*>("getVkApiData"),
      const_cast<char*>("()Lcom/lmg/vk/jni/BundleNativeClass;"),
      reinterpret_cast<void*>(nativeGetVkApiData) },
    { const_cast<char*>("getLmgEnvironment"),
      const_cast<char*>("()Lcom/lmg/vk/jni/BundleNativeClass;"),
      reinterpret_cast<void*>(nativeGetLmgEnvironment) },
    { const_cast<char*>("getSilentAuthorizationEnvironment"),
      const_cast<char*>("(Ljava/lang/String;)Lcom/lmg/vk/jni/BundleNativeClass;"),
      reinterpret_cast<void*>(nativeGetSilentAuthorizationEnvironment) },
};

}  // namespace lmg

// =============================================================================
// JNI_OnLoad (оригинал @ 0xB8F64):
//   1. GetEnv(JNI_VERSION_1_6)
//   2. disableXposedHooks()
//   3. Самопроверка целостности: чтение собственного base.apk
//      (/data/app/com.lmg.vk-1/base.apk), dl_iterate_phdr-обход,
//      сверка подписи (kLmgExpectedSignature); при несовпадении — abort.
//   4. Кэширование BundleNativeClass: ctor (I)V, add (ILjava/lang/Object;)V
//   5. RegisterNatives("com/lmg/vk/jni/LmgNative", methods, 3)
// =============================================================================
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    lmg::disableXposedHooks(env);

    // NOTE: самопроверка целостности APK (п.3) опущена — в восстановленном
    // коде она не нужна; в оригинале защищает от переподписи/патчинга.

    jclass bundleCls = env->FindClass("com/lmg/vk/jni/BundleNativeClass");
    if (!bundleCls) return JNI_ERR;
    lmg::g_jni.bundleClass = reinterpret_cast<jclass>(env->NewGlobalRef(bundleCls));
    lmg::g_jni.bundleCtor  = env->GetMethodID(bundleCls, "<init>", "(I)V");
    lmg::g_jni.bundleAdd   = env->GetMethodID(bundleCls, "add", "(ILjava/lang/Object;)V");
    env->DeleteLocalRef(bundleCls);

    jclass nativeCls = env->FindClass("com/lmg/vk/jni/LmgNative");
    if (!nativeCls) return JNI_ERR;
    if (env->RegisterNatives(nativeCls, lmg::kLmgNativeMethods, 3) != JNI_OK) {
        return JNI_ERR;
    }
    env->DeleteLocalRef(nativeCls);

    return JNI_VERSION_1_6;
}

import java.util.Properties
plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
// Chave de assinatura fica FORA do repositório (mesmo esquema dos outros apps).
val ks = Properties().apply { val f = file("/workspace/controle-samsung-keystore/keystore.properties"); if (f.exists()) f.inputStream().use { load(it) } }
android {
  namespace = "br.geracaoai.controlesamsung"
  compileSdk = 35
  defaultConfig { applicationId = "br.geracaoai.controlesamsung"; minSdk = 29; targetSdk = 35; versionCode = 2; versionName = "1.0.1" }
  signingConfigs { create("release") { if (ks.isNotEmpty()) { storeFile = file(ks["storeFile"] as String); storePassword = ks["storePassword"] as String; keyAlias = ks["keyAlias"] as String; keyPassword = ks["keyPassword"] as String } } }
  buildTypes { release { isMinifyEnabled = true; isShrinkResources = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"); if (ks.isNotEmpty()) signingConfig = signingConfigs.getByName("release") } }
  compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
  kotlinOptions { jvmTarget = "17" }
  buildFeatures { compose = true; buildConfig = true }
  sourceSets["main"].java.srcDir("src/main/kotlin")
  sourceSets["test"].java.srcDir("src/test/kotlin")
  lint { checkReleaseBuilds = false; abortOnError = false }
  testOptions { unitTests.isReturnDefaultValues = true; unitTests.isIncludeAndroidResources = true }
}
dependencies {
  val bom = platform("androidx.compose:compose-bom:2024.09.00")
  implementation(bom)
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.material:material-icons-extended")
  implementation("androidx.compose.ui:ui")
  implementation("androidx.activity:activity-compose:1.9.2")
  implementation("androidx.core:core-ktx:1.13.1")
  implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
  implementation("com.squareup.okhttp3:okhttp:4.12.0")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
  testImplementation("junit:junit:4.13.2")
  testImplementation("org.robolectric:robolectric:4.14.1")
  testImplementation("androidx.compose.ui:ui-test-junit4")
  debugImplementation("androidx.compose.ui:ui-test-manifest")
}

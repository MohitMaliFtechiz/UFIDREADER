# UHF-SRS-001 SDK-10. Keep the SDK's own public API surface intact under R8/minification in a
# consuming app; the platform's android.hardware.uhf classes are framework code and are never
# stripped or renamed by the app's own R8 pass regardless, so only this module's classes need a
# rule here.
-keep public class com.rockchip.uhf.sdk.UhfSdk { public *; }
-keep public interface com.rockchip.uhf.sdk.UhfSdkSession { public *; }
-keep public class com.rockchip.uhf.sdk.FakeUhfSdkSession { public *; }
-keep class com.rockchip.uhf.sdk.UhfSdkKt { public *; }

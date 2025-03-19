package dev.rushii.xspoofsignatures;

import android.content.pm.*;
import android.os.Build;
import android.util.Log;

import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

@SuppressWarnings("deprecation")
public class Main implements IXposedHookLoadPackage {
	private static final String TAG = "XSpoofSignatures";

	private static boolean isFetchingSignatures(long flags) {
		int mask = PackageManager.GET_SIGNATURES |
			(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? PackageManager.GET_SIGNING_CERTIFICATES : 0);

		return (flags & mask) != 0;
	}

	// Copy all signatures to a new array with an extra sig at the beginning
	private static Signature[] copySignatures(Signature[] orig, Signature extra) {
		Signature[] signatures = new Signature[orig.length + 1];
		signatures[0] = extra;
		System.arraycopy(orig, 0, signatures, 1, orig.length);
		return signatures;
	}

	private static String getHookClassName() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			// https://cs.android.com/android/platform/superproject/+/android-15.0.0_r1:frameworks/base/services/core/java/com/android/server/pm/ComputerEngine.java;l=1484;drc=d970c566017e2c4a69e545775994fc46e0869247
			return "com.android.server.pm.ComputerEngine";
		} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			// https://cs.android.com/android/platform/superproject/+/android-12.0.0_r1:frameworks/base/services/core/java/com/android/server/pm/PackageManagerService.java;l=3302;drc=2cf61babf8de1e5e3a45770632fa067556021291
			return "com.android.server.pm.PackageManagerService$ComputerEngine";
		} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			// https://cs.android.com/android/platform/superproject/+/android-4.0.1_r1:frameworks/base/services/java/com/android/server/pm/PackageManagerService.java;l=1485;drc=58f42a59bda3bc912d0d2f81dc65a9d31d140eaa
			return "com.android.server.pm.PackageManagerService";
		} else {
			// Android SRC is unavailable for Android Honeycomb (3.x) and Android Cupcake (1.5)
			// but I confirmed this is accurate for those versions by extracting the system image

			// https://cs.android.com/android/platform/superproject/+/android-2.2.3_r1:frameworks/base/services/java/com/android/server/PackageManagerService.java;l=1316;drc=e2fd45af93178b30e6da97b46fcd31b7d30f5426
			return "com.android.server.PackageManagerService";
		}
	}

	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) {
		if (!"android".equals(lpparam.packageName)) return;

		XC_MethodHook hook = new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) {
				// https://cs.android.com/android/platform/superproject/+/android-12.1.0_r5:frameworks/base/services/core/java/com/android/server/pm/PackageManagerService.java;l=3325;drc=32796cd24bf5b392d5e823d4c6abc4e2f1dfe4a2
				// https://cs.android.com/android/platform/superproject/+/android-13.0.0_r1:frameworks/base/services/core/java/com/android/server/pm/ComputerEngine.java;l=1594;drc=66acf93106a784172c39e6bbf5c22a1aa3563e0b
				long flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
					? (long) param.args[1]
					: (long) (int) param.args[1];

				// Avoid getting metadata when not needed
				if (!isFetchingSignatures(flags)) return;

				flags |= PackageManager.GET_META_DATA | PackageManager.GET_PERMISSIONS;

				// Explicit cast to Object is needed, otherwise the compiler boxes the value into a Long for both branches??
				// I hate java
				param.args[1] = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
					? (Object) flags
					: (Object) (int) flags;
			}

			@Override
			protected void afterHookedMethod(MethodHookParam param) {
				PackageInfo pi = (PackageInfo) param.getResult();
				if (pi == null) return;

				long flags = param.args[1] instanceof Integer
					? (long) (int) param.args[1]
					: (long) param.args[1];
				if (!isFetchingSignatures(flags)) return;

				// Get the declared fake signature from manifest meta-data
				String fakeSig;
				if (pi.applicationInfo == null ||
					pi.applicationInfo.metaData == null ||
					(fakeSig = pi.applicationInfo.metaData.getString("fake-signature")) == null) {
					return;
				}

				// Check if the permission was granted
				if (pi.requestedPermissions == null || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN &&
					pi.requestedPermissionsFlags == null)) return;
				boolean granted = false;
				for (int i = 0; i < pi.requestedPermissions.length; i++) {
					if ("android.permission.FAKE_PACKAGE_SIGNATURE".equals(pi.requestedPermissions[i]) &&
						(pi.requestedPermissionsFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) {
						granted = true;
						break;
					}
				}
				if (!granted) return;

				// Check if whether to preserve the other signatures
				boolean makeSoleSigner = pi.applicationInfo.metaData.getBoolean("fake-signature-only", true);

				Log.d(TAG, "Spoofing signature for " + pi.packageName);

				if (pi.signatures != null) {
					Signature sig = new Signature(fakeSig);
					if (makeSoleSigner) {
						pi.signatures = new Signature[]{ sig };
					} else {
						pi.signatures = copySignatures(pi.signatures, sig);
					}
				}

				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && pi.signingInfo != null) {
					Signature[] origSignatures = pi.signingInfo.getApkContentsSigners();
					Signature sig = new Signature(fakeSig);

					if (makeSoleSigner && origSignatures.length == 1) {
						origSignatures[0] = sig;
					} else {
						Object signingDetails = XposedHelpers.getObjectField(pi.signingInfo, "mSigningDetails");
						Signature[] newSignatures;

						if (makeSoleSigner) {
							newSignatures = new Signature[]{ sig };
						} else {
							// SigningInfo#mSigningDetails (SigningDetails)
							newSignatures = copySignatures(origSignatures, sig);
						}

						String targetField;
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
							// SigningDetails#mSignatures (Signature[])
							targetField = "mSignatures";
						} else {
							// SV2 and below
							// PackageParser$SigningDetails#signatures (Signature[])
							targetField = "signatures";
						}
						XposedHelpers.setObjectField(signingDetails, targetField, newSignatures);
					}
				}
			}
		};

		final String hookClassName = getHookClassName();
		final Class<?> hookClass = XposedHelpers.findClass(hookClassName, lpparam.classLoader);
		XposedBridge.hookAllMethods(hookClass, "generatePackageInfo", hook);
		Log.d(TAG, String.format("Hooking all %s#generatePackageInfo(...)", hookClassName));
	}
}

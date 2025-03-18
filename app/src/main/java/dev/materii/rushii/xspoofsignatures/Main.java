package dev.materii.rushii.xspoofsignatures;

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

	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) {
		if (!"android".equals(lpparam.packageName)) return;

		XC_MethodHook hook = new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) {
				long flags = (long) param.args[1];

				// Avoid getting metadata when not needed
				if (!isFetchingSignatures(flags)) return;

				param.args[1] = flags | PackageManager.GET_META_DATA | PackageManager.GET_PERMISSIONS;
			}

			@Override
			protected void afterHookedMethod(MethodHookParam param) {
				PackageInfo pi = (PackageInfo) param.getResult();
				if (pi == null) return;

				long flags = (long) param.args[1];
				if (!isFetchingSignatures(flags)) return;

				// Get the declared fake signature from manifest meta-data
				String fakeSig;
				if (pi.applicationInfo == null ||
					pi.applicationInfo.metaData == null ||
					(fakeSig = pi.applicationInfo.metaData.getString("fake-signature")) == null) {
					return;
				}

				// Check if the permission was granted
				if (pi.requestedPermissions == null ||
					pi.requestedPermissionsFlags == null) return;
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

		String targetClass;
		switch (Build.VERSION.SDK_INT) {
			case Build.VERSION_CODES.UPSIDE_DOWN_CAKE:
			case Build.VERSION_CODES.TIRAMISU:
				targetClass = "com.android.server.pm.ComputerEngine";
				break;
			case Build.VERSION_CODES.S_V2:
			case Build.VERSION_CODES.S:
				targetClass = "com.android.server.pm.PackageManagerService$ComputerEngine";
				break;
			default:
				targetClass = "com.android.server.pm.PackageManagerService";
				break;
		}

		final Class<?> hookClass = XposedHelpers.findClass(targetClass, lpparam.classLoader);
		XposedBridge.hookAllMethods(hookClass, "generatePackageInfo", hook);
		Log.d(TAG, String.format("Hooking all %s#generatePackageInfo(...)", targetClass));
	}
}

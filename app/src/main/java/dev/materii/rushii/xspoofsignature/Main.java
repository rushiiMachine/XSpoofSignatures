package dev.materii.rushii.xspoofsignature;

import android.content.pm.*;
import android.os.Build;
import android.util.Log;

import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class Main implements IXposedHookLoadPackage {
	private static final String TAG = "XSpoofSignatures";

	private boolean isFetchingSignatures(int flags) {
		int mask = PackageManager.GET_SIGNATURES |
			(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? PackageManager.GET_SIGNING_CERTIFICATES : 0);

		return (flags & mask) != 0;
	}

	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) {
		if (!"android".equals(lpparam.packageName)) return;

		XC_MethodHook hook = new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) {
				int flags = (int) param.args[1];

				// Avoid getting metadata when not needed
				if (!isFetchingSignatures(flags)) return;

				param.args[1] = flags | PackageManager.GET_META_DATA | PackageManager.GET_PERMISSIONS;
			}

			@Override
			protected void afterHookedMethod(MethodHookParam param) {
				PackageInfo pi = (PackageInfo) param.getResult();
				if (pi == null) return;

				int flags = (int) param.args[1];
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

				Log.d(TAG, "Spoofing signature for " + pi.packageName);

				if (pi.signatures != null) {
					pi.signatures = new Signature[]{ new Signature(fakeSig) };
				}

				Signature[] sigInfoSignatures;
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
					&& pi.signingInfo != null
					&& (sigInfoSignatures = pi.signingInfo.getApkContentsSigners()).length > 0) {

					// Not sure if this will maintain compatibility when multiple signers exist
					sigInfoSignatures[0] = new Signature(fakeSig);
				}
			}
		};

		String targetClass;
		switch (Build.VERSION.SDK_INT) {
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

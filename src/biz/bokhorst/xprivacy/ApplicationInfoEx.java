package biz.bokhorst.xprivacy;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

@SuppressLint("DefaultLocale")
public class ApplicationInfoEx implements Comparable<ApplicationInfoEx> {
	private TreeMap<String, ApplicationInfo> mMapAppInfo = null;
	private Map<String, PackageInfo> mMapPkgInfo = new HashMap<String, PackageInfo>();

	// Cache
	private Drawable mIcon = null;
	private boolean mInternet = false;
	private boolean mInternetDetermined = false;
	private boolean mFrozen = false;
	private boolean mFrozenDetermined = false;
	private long mInstallTime = -1;
	private long mUpdateTime = -1;

	public ApplicationInfoEx(Context context, int uid) {
		mMapAppInfo = new TreeMap<String, ApplicationInfo>();
		PackageManager pm = context.getPackageManager();
		String[] packages = pm.getPackagesForUid(uid);
		if (packages != null)
			for (String packageName : packages)
				try {
					ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
					mMapAppInfo.put(pm.getApplicationLabel(appInfo).toString(), appInfo);
				} catch (NameNotFoundException ignored) {
				}
	}

	public static List<ApplicationInfoEx> getXApplicationList(Context context, ProgressDialog dialog) {
		// Get references
		PackageManager pm = context.getPackageManager();

		// Get app list
		SparseArray<ApplicationInfoEx> mapApp = new SparseArray<ApplicationInfoEx>();
		List<ApplicationInfoEx> listApp = new ArrayList<ApplicationInfoEx>();
		List<ApplicationInfo> listAppInfo = pm.getInstalledApplications(PackageManager.GET_META_DATA);
		if (dialog != null)
			dialog.setMax(listAppInfo.size());
		for (int app = 0; app < listAppInfo.size(); app++) {
			if (dialog != null)
				dialog.setProgress(app + 1);

			ApplicationInfo appInfo = listAppInfo.get(app);
			Util.log(null, Log.INFO, "package=" + appInfo.packageName + " uid=" + appInfo.uid);

			ApplicationInfoEx appInfoEx = new ApplicationInfoEx(context, appInfo.uid);
			if (mapApp.get(appInfoEx.getUid()) == null) {
				mapApp.put(appInfoEx.getUid(), appInfoEx);
				listApp.add(appInfoEx);
			}
		}

		// Sort result
		Collections.sort(listApp);
		return listApp;
	}

	public List<String> getApplicationName() {
		return new ArrayList<String>(mMapAppInfo.navigableKeySet());
	}

	public String getApplicationName(String packageName) {
		for (Entry<String, ApplicationInfo> entry : mMapAppInfo.entrySet())
			if (entry.getValue().packageName.equals(packageName))
				return entry.getKey();
		return "";
	}

	public List<String> getPackageName() {
		List<String> listPackageName = new ArrayList<String>();
		for (ApplicationInfo appInfo : mMapAppInfo.values())
			listPackageName.add(appInfo.packageName);
		return listPackageName;
	}

	private void getPackageInfo(Context context, String packageName) throws NameNotFoundException {
		PackageManager pm = context.getPackageManager();
		mMapPkgInfo.put(packageName, pm.getPackageInfo(packageName, 0));
	}

	public List<String> getPackageVersionName(Context context) {
		List<String> listVersionName = new ArrayList<String>();
		for (String packageName : this.getPackageName())
			try {
				getPackageInfo(context, packageName);
				String version = mMapPkgInfo.get(packageName).versionName;
				if (version == null)
					listVersionName.add("???");
				else
					listVersionName.add(version);
			} catch (NameNotFoundException ex) {
				listVersionName.add(ex.getMessage());
			}
		return listVersionName;
	}

	public String getPackageVersionName(Context context, String packageName) {
		try {
			getPackageInfo(context, packageName);
			String version = mMapPkgInfo.get(packageName).versionName;
			if (version == null)
				return "???";
			else
				return version;
		} catch (NameNotFoundException ex) {
			return ex.getMessage();
		}
	}

	public List<Integer> getPackageVersionCode(Context context) {
		List<Integer> listVersionCode = new ArrayList<Integer>();
		for (String packageName : this.getPackageName())
			try {
				getPackageInfo(context, packageName);
				listVersionCode.add(mMapPkgInfo.get(packageName).versionCode);
			} catch (NameNotFoundException ex) {
				listVersionCode.add(0);
			}
		return listVersionCode;
	}

	public Drawable getIcon(Context context) {
		if (mIcon == null)
			// Pick first icon
			if (mMapAppInfo.size() > 0)
				mIcon = mMapAppInfo.firstEntry().getValue().loadIcon(context.getPackageManager());
		return (mIcon == null ? new ColorDrawable(Color.TRANSPARENT) : mIcon);
	}

	public boolean hasInternet(Context context) {
		if (!mInternetDetermined) {
			PackageManager pm = context.getPackageManager();
			for (ApplicationInfo appInfo : mMapAppInfo.values())
				if (pm.checkPermission("android.permission.INTERNET", appInfo.packageName) == PackageManager.PERMISSION_GRANTED) {
					mInternet = true;
					break;
				}
			mInternetDetermined = true;
		}
		return mInternet;
	}

	public boolean isFrozen(Context context) {
		if (!mFrozenDetermined) {
			PackageManager pm = context.getPackageManager();
			boolean enabled = false;
			for (ApplicationInfo appInfo : mMapAppInfo.values()) {
				int setting = pm.getApplicationEnabledSetting(appInfo.packageName);
				enabled = (enabled || setting == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);
				enabled = (enabled || setting == PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
				if (enabled)
					break;
			}
			mFrozen = !enabled;
			mFrozenDetermined = true;
		}
		return mFrozen;
	}

	public int getUid() {
		// All listed uid's are the same
		return mMapAppInfo.firstEntry().getValue().uid;
	}

	public int getState(Context context) {
		return Integer.parseInt(PrivacyManager.getSetting(getUid(), PrivacyManager.cSettingState, "1", false));
	}

	public long getInstallTime(Context context) {
		if (mInstallTime == -1) {
			long now = System.currentTimeMillis();
			mInstallTime = now;
			for (String packageName : this.getPackageName())
				try {
					getPackageInfo(context, packageName);
					long time = mMapPkgInfo.get(packageName).firstInstallTime;
					if (time < mInstallTime)
						mInstallTime = time;
				} catch (NameNotFoundException ex) {
				}
			if (mInstallTime == now)
				// no install time, so assume it is old
				mInstallTime = 0;
		}
		return mInstallTime;
	}

	public long getUpdateTime(Context context) {
		if (mUpdateTime == -1) {
			mUpdateTime = 0;
			for (String packageName : this.getPackageName())
				try {
					getPackageInfo(context, packageName);
					long time = mMapPkgInfo.get(packageName).lastUpdateTime;
					if (time > mUpdateTime)
						mUpdateTime = time;
				} catch (NameNotFoundException ex) {
				}
		}
		return mUpdateTime;
	}

	public long getModificationTime(Context context) {
		return Long.parseLong(PrivacyManager.getSetting(getUid(), PrivacyManager.cSettingModifyTime, "0", false));
	}

	public boolean isSystem() {
		boolean mSystem = false;
		for (ApplicationInfo appInfo : mMapAppInfo.values()) {
			mSystem = ((appInfo.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0);
			mSystem = mSystem || appInfo.packageName.equals(this.getClass().getPackage().getName());
			mSystem = mSystem || appInfo.packageName.equals(this.getClass().getPackage().getName() + ".pro");
			mSystem = mSystem || appInfo.packageName.equals("de.robv.android.xposed.installer");
		}
		return mSystem;
	}

	public boolean isShared() {
		for (ApplicationInfo appInfo : mMapAppInfo.values())
			if (PrivacyManager.isShared(appInfo.uid))
				return true;
		return false;
	}

	public boolean isIsolated() {
		for (ApplicationInfo appInfo : mMapAppInfo.values())
			if (PrivacyManager.isIsolated(appInfo.uid))
				return true;
		return false;
	}

	@Override
	public String toString() {
		// All uid's are the same
		return String.format("%d %s", mMapAppInfo.firstEntry().getValue().uid,
				TextUtils.join(", ", getApplicationName()));
	}

	@Override
	public int compareTo(ApplicationInfoEx other) {
		// Locale respecting sorter
		Collator collator = Collator.getInstance(Locale.getDefault());
		return collator.compare(TextUtils.join(", ", getApplicationName()),
				TextUtils.join(", ", other.getApplicationName()));
	}
}

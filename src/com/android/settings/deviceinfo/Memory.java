/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.deviceinfo;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.DialogInterface.OnCancelListener;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.Environment;
import android.os.storage.IMountService;
import android.os.ServiceManager;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.os.storage.StorageEventListener;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.text.format.Formatter;
import android.util.Log;
import android.widget.Toast;

import com.android.settings.R;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Memory extends PreferenceActivity implements OnCancelListener {
    private static final String TAG = "Memory";
    private static final boolean localLOGV = false;

    private static final String MEMORY_SD_SIZE = "memory_sd_size";

    private static final String MEMORY_SD_AVAIL = "memory_sd_avail";

    private static final String MEMORY_SD_MOUNT_TOGGLE = "memory_sd_mount_toggle";

    private static final String MEMORY_SD_FORMAT = "memory_sd_format";

    private static final String MEMORY_EMMC_SIZE = "memory_emmc_size";

    private static final String MEMORY_EMMC_AVAIL = "memory_emmc_avail";

    private static final String MEMORY_EMMC_MOUNT_TOGGLE = "memory_emmc_mount_toggle";

    private static final String MEMORY_EMMC_FORMAT = "memory_emmc_format";


    private static final int DLG_CONFIRM_UNMOUNT = 1;
    private static final int DLG_ERROR_UNMOUNT = 2;

    private Resources mRes;

    private Preference mSdSize;
    private Preference mSdAvail;
    private Preference mSdMountToggle;
    private Preference mSdFormat;
    
    private Preference mEmmcSize;
    private Preference mEmmcAvail;
    private Preference mEmmcMountToggle;
    private Preference mEmmcFormat;

    // Access using getMountService()
    private IMountService mMountService = null;

    private StorageManager mStorageManager = null;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        if (mStorageManager == null) {
            mStorageManager = (StorageManager) getSystemService(Context.STORAGE_SERVICE);
            mStorageManager.registerListener(mStorageListener);
        }

        addPreferencesFromResource(R.xml.device_info_memory);
        
        mRes = getResources();
        mSdSize = findPreference(MEMORY_SD_SIZE);
        mSdAvail = findPreference(MEMORY_SD_AVAIL);
        mSdMountToggle = findPreference(MEMORY_SD_MOUNT_TOGGLE);
        mSdFormat = findPreference(MEMORY_SD_FORMAT);

        mEmmcSize = findPreference(MEMORY_EMMC_SIZE);
        mEmmcAvail = findPreference(MEMORY_EMMC_AVAIL);
        mEmmcMountToggle = findPreference(MEMORY_EMMC_MOUNT_TOGGLE);
        mEmmcFormat = findPreference(MEMORY_EMMC_FORMAT);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_MEDIA_SCANNER_STARTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
        intentFilter.addDataScheme("file");
        registerReceiver(mReceiver, intentFilter);

        updateMemoryStatus();
    }

    StorageEventListener mStorageListener = new StorageEventListener() {

        @Override
        public void onStorageStateChanged(String path, String oldState, String newState) {
            Log.i(TAG, "Received storage state changed notification that " +
                    path + " changed state from " + oldState +
                    " to " + newState);
            updateMemoryStatus();
        }
    };
    
    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mReceiver);
    }

    @Override
    protected void onDestroy() {
        if (mStorageManager != null && mStorageListener != null) {
            mStorageManager.unregisterListener(mStorageListener);
        }
        super.onDestroy();
    }

    private synchronized IMountService getMountService() {
       if (mMountService == null) {
           IBinder service = ServiceManager.getService("mount");
           if (service != null) {
               mMountService = IMountService.Stub.asInterface(service);
           } else {
               Log.e(TAG, "Can't get mount service");
           }
       }
       return mMountService;
    }
    
    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mSdMountToggle) {
            String status = Environment.getExternalStorageState();
            if (status.equals(Environment.MEDIA_MOUNTED)) {
                unmount(Environment.getExternalStorageDirectory(), mSdMountToggle);
            } else {
                mount(Environment.getExternalStorageDirectory());
            }
            return true;
        } else if (preference == mSdFormat) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.putExtra("storage", Environment.getExternalStorageDirectory().toString());
            intent.setClass(this, com.android.settings.MediaFormat.class);
            startActivity(intent);
            return true;
        } else if (preference == mEmmcMountToggle) {
            String status = Environment.getInternalStorageState();
            if (status.equals(Environment.MEDIA_MOUNTED)) {
                unmount(Environment.getInternalStorageDirectory(), mEmmcMountToggle);
            } else {
                mount(Environment.getInternalStorageDirectory());
            }
            return true;
        } else if (preference == mEmmcFormat) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.putExtra("storage", Environment.getInternalStorageDirectory().toString());
            intent.setClass(this, com.android.settings.MediaFormat.class);
            startActivity(intent);
            return true;
        }
        
        return false;
    }
     
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateMemoryStatus();
        }
    };

    @Override
    public Dialog onCreateDialog(int id, Bundle args) {
        switch (id) {
        case DLG_CONFIRM_UNMOUNT:
            return new AlertDialog.Builder(this)
                    .setTitle(R.string.dlg_confirm_unmount_title)
                    .setPositiveButton(R.string.dlg_ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            doUnmount(Environment.getExternalStorageDirectory(),
                                      mSdMountToggle,
                                      true);
                        }})
                    .setNegativeButton(R.string.cancel, null)
                    .setMessage(R.string.dlg_confirm_unmount_text)
                    .setOnCancelListener(this)
                    .create();
        case DLG_ERROR_UNMOUNT:
            return new AlertDialog.Builder(this                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            )
            .setTitle(R.string.dlg_error_unmount_title)
            .setNeutralButton(R.string.dlg_ok, null)
            .setMessage(R.string.dlg_error_unmount_text)
            .setOnCancelListener(this)
            .create();
        }
        return null;
    }

    private void doUnmount(File path, Preference mountToggle, boolean force) {
        // Present a toast here
        Toast.makeText(this, R.string.unmount_inform_text, Toast.LENGTH_SHORT).show();
        IMountService mountService = getMountService();
        String extStoragePath = path.toString();
        try {
            mountToggle.setEnabled(false);
            mountToggle.setTitle(mRes.getString(R.string.sd_ejecting_title));
            mountToggle.setSummary(mRes.getString(R.string.sd_ejecting_summary));
            mountService.unmountVolume(extStoragePath, force);
        } catch (RemoteException e) {
            // Informative dialog to user that
            // unmount failed.
            showDialogInner(DLG_ERROR_UNMOUNT);
        }
    }

    private void showDialogInner(int id) {
        removeDialog(id);
        showDialog(id);
    }

    private boolean hasAppsAccessingStorage(File path) throws RemoteException {
        String extStoragePath = path.toString();
        IMountService mountService = getMountService();
        boolean showPidDialog = false;
        int stUsers[] = mountService.getStorageUsers(extStoragePath);
        if (stUsers != null && stUsers.length > 0) {
            return true;
        }
        ActivityManager am = (ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
        List<ApplicationInfo> list = am.getRunningExternalApplications();
        if (list != null && list.size() > 0) {
            return true;
        }
        return false;
    }

    private void unmount(File path, Preference mountToggle) {
        // Check if external media is in use.
        try {
           if (hasAppsAccessingStorage(path)) {
               if (localLOGV) Log.i(TAG, "Do have storage users accessing media");
               // Present dialog to user
               showDialogInner(DLG_CONFIRM_UNMOUNT);
           } else {
               doUnmount(path, mountToggle, true);
           }
        } catch (RemoteException e) {
            // Very unlikely. But present an error dialog anyway
            Log.e(TAG, "Is MountService running?");
            showDialogInner(DLG_ERROR_UNMOUNT);
        }
    }

    private void mount(File path) {
        IMountService mountService = getMountService();
        try {
            if (mountService != null) {
                mountService.mountVolume(path.toString());
            } else {
                Log.e(TAG, "Mount service is null, can't mount");
            }
        } catch (RemoteException ex) {
        }
    }

    private void updateSdMemoryStatus(String status, File path, Preference size, Preference avail,
            Preference mountToggle, Preference format) {
        String readOnly = "";
        if (status.equals(Environment.MEDIA_MOUNTED_READ_ONLY)) {
            status = Environment.MEDIA_MOUNTED;
            readOnly = mRes.getString(R.string.read_only);
        }

        format.setEnabled(false);

        if (status.equals(Environment.MEDIA_MOUNTED)) {
            try {
                StatFs stat = new StatFs(path.getPath());
                long blockSize = stat.getBlockSize();
                long totalBlocks = stat.getBlockCount();
                long availableBlocks = stat.getAvailableBlocks();

                size.setSummary(formatSize(totalBlocks * blockSize));
                avail.setSummary(formatSize(availableBlocks * blockSize) + readOnly);

                mountToggle.setEnabled(true);
                mountToggle.setTitle(mRes.getString(R.string.sd_eject));
                mountToggle.setSummary(mRes.getString(R.string.sd_eject_summary));
            } catch (IllegalArgumentException e) {
                // this can occur if the SD card is removed, but we haven't received the 
                // ACTION_MEDIA_REMOVED Intent yet.
                status = Environment.MEDIA_REMOVED;
            }
        } else {
            size.setSummary(mRes.getString(R.string.sd_unavailable));
            avail.setSummary(mRes.getString(R.string.sd_unavailable));

            if (status.equals(Environment.MEDIA_UNMOUNTED) ||
                    status.equals(Environment.MEDIA_NOFS) ||
                    status.equals(Environment.MEDIA_UNMOUNTABLE) ) {
                format.setEnabled(true);
                mountToggle.setEnabled(true);
                mountToggle.setTitle(mRes.getString(R.string.sd_mount));
                mountToggle.setSummary(mRes.getString(R.string.sd_mount_summary));
            } else {
                mountToggle.setEnabled(false);
                mountToggle.setTitle(mRes.getString(R.string.sd_mount));
                mountToggle.setSummary(mRes.getString(R.string.sd_insert_summary));
            }
        }
    }

    private void updateMemoryStatus() {
        updateSdMemoryStatus(Environment.getInternalStorageState(),
                             Environment.getInternalStorageDirectory(),
                             mEmmcSize,
                             mEmmcAvail,
                             mEmmcMountToggle,
                             mEmmcFormat);

        updateSdMemoryStatus(Environment.getExternalStorageState(),
                             Environment.getExternalStorageDirectory(),
                             mSdSize,
                             mSdAvail,
                             mSdMountToggle,
                             mSdFormat);

        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSize();
        long availableBlocks = stat.getAvailableBlocks();
        findPreference("memory_internal_avail").setSummary(formatSize(availableBlocks * blockSize));
    }
    
    private String formatSize(long size) {
        return Formatter.formatFileSize(this, size);
    }

    public void onCancel(DialogInterface dialog) {
        finish();
    }
    
}

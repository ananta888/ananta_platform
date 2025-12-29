package com.sovworks.eds.android.settings.fragments;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import com.sovworks.eds.android.EdsApplication;
import com.sovworks.eds.android.Logger;
import com.sovworks.eds.android.R;
import com.sovworks.eds.android.dialogs.MasterPasswordDialog;
import com.sovworks.eds.android.dialogs.PasswordDialog;
import com.sovworks.eds.android.filemanager.activities.FileManagerActivity;
import com.sovworks.eds.android.fragments.PropertiesFragmentBase;
import com.sovworks.eds.android.settings.ButtonPropertyEditor;
import com.sovworks.eds.android.settings.CategoryPropertyEditor;
import com.sovworks.eds.android.settings.ChoiceDialogPropertyEditor;
import com.sovworks.eds.android.settings.IntPropertyEditor;
import com.sovworks.eds.android.settings.MultilineTextPropertyEditor;
import com.sovworks.eds.android.settings.PathPropertyEditor;
import com.sovworks.eds.android.settings.SwitchPropertyEditor;
import com.sovworks.eds.android.settings.TextPropertyEditor;
import com.sovworks.eds.android.settings.UserSettings;
import com.sovworks.eds.android.settings.program.ExtFileManagerPropertyEditor;
import com.sovworks.eds.android.settings.program.InstallExFatModulePropertyEditor;
import com.sovworks.eds.android.network.WebRtcService;
import com.sovworks.eds.android.identity.IdentityManager;
import com.sovworks.eds.crypto.SecureBuffer;
import com.sovworks.eds.crypto.TwoFactorAuth;
import com.sovworks.eds.fs.util.PathUtil;
import com.sovworks.eds.locations.Location;
import com.sovworks.eds.locations.LocationsManager;
import com.sovworks.eds.settings.Settings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.sovworks.eds.android.settings.UserSettingsCommon.DISABLE_DEBUG_LOG;
import static com.sovworks.eds.android.settings.UserSettingsCommon.DISABLE_MODIFIED_FILES_BACKUP;
import static com.sovworks.eds.android.settings.UserSettingsCommon.DISABLE_WIDE_SCREEN_LAYOUTS;
import static com.sovworks.eds.android.settings.UserSettingsCommon.DONT_USE_CONTENT_PROVIDER;
import static com.sovworks.eds.android.settings.UserSettingsCommon.EXTENSIONS_MIME;
import static com.sovworks.eds.android.settings.UserSettingsCommon.FORCE_TEMP_FILES;
import static com.sovworks.eds.android.settings.UserSettingsCommon.IS_FLAG_SECURE_ENABLED;
import static com.sovworks.eds.android.settings.UserSettingsCommon.MAX_FILE_SIZE_TO_OPEN;
import static com.sovworks.eds.android.settings.UserSettingsCommon.NEVER_SAVE_HISTORY;
import static com.sovworks.eds.android.settings.UserSettingsCommon.SIGNALING_MODE;
import static com.sovworks.eds.android.settings.UserSettingsCommon.SIGNALING_MODE_HTTP;
import static com.sovworks.eds.android.settings.UserSettingsCommon.SIGNALING_SERVER_URL;
import static com.sovworks.eds.android.settings.UserSettingsCommon.SHOW_PREVIEWS;
import static com.sovworks.eds.android.settings.UserSettingsCommon.THEME;
import static com.sovworks.eds.android.settings.UserSettingsCommon.USE_INTERNAL_IMAGE_VIEWER;
import static com.sovworks.eds.android.settings.UserSettingsCommon.WIPE_TEMP_FILES;
import static com.sovworks.eds.android.settings.UserSettingsCommon.WORK_DIR;
import static com.sovworks.eds.settings.SettingsCommon.THEME_DARK;
import static com.sovworks.eds.settings.SettingsCommon.THEME_DEFAULT;

public abstract class ProgramSettingsFragmentBase extends PropertiesFragmentBase implements MasterPasswordDialog.PasswordReceiver
{
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        _settings = UserSettings.getSettings(getActivity());
        super.onCreate(savedInstanceState);
    }

    public UserSettings getSettings()
    {
        return _settings;
    }

    public SharedPreferences.Editor editSettings()
    {
        return _settings.getSharedPreferences().edit();
    }

    //master password is set
    @Override
    public void onPasswordEntered(PasswordDialog dlg)
    {
        char[] data = dlg.getPassword();
        if(data != null && data.length == 0)
            data = null;

        EdsApplication.setMasterPassword(data == null ? null : new SecureBuffer(data));
        try
        {
            _settings.saveSettingsProtectionKey();
        }
        catch (Settings.InvalidSettingsPassword ignored)
        {
        }
    }

    //master password is not set
    @Override
    public void onPasswordNotEntered(PasswordDialog dlg)
    {

    }

    protected UserSettings _settings;

    @Override
    protected void createProperties()
    {
        getPropertiesView().setInstantSave(true);
        createCategories();
        _propertiesView.setPropertiesState(false);
        _propertiesView.setPropertyState(R.string.main_settings, true);
    }

    protected void createCategories()
    {
        final List<Integer> commonPropertiesList = new ArrayList<>();
        getPropertiesView().addProperty(new CategoryPropertyEditor(this, R.string.main_settings, 0)
        {
            @Override
            public void load()
            {
                enableProperties(commonPropertiesList, isExpanded());
            }
        });
        createCommonProperties(commonPropertiesList);
    }

    protected void createCommonProperties(List<Integer> commonPropertiesIds)
    {
        commonPropertiesIds.add(getPropertiesView().addProperty(
                new ChoiceDialogPropertyEditor(this, R.string.theme, R.string.theme_desc, getTag())
        {
            @Override
            protected int loadValue()
            {
                return _settings.getCurrentTheme() == THEME_DEFAULT ? 0 : 1;
            }

            @Override
            protected void saveValue(int value)
            {
                editSettings().putInt(THEME, value == 0 ? THEME_DEFAULT : THEME_DARK).commit();
            }

            @Override
            protected List<String> getEntries()
            {
                return Arrays.asList(getString(R.string.default_theme), getString(R.string.dark_theme));
            }
        }));
        commonPropertiesIds.add(getPropertiesView().addProperty(new ButtonPropertyEditor(this, R.string.master_password, 0, R.string.enter_master_password)
        {
            @Override
            protected void onButtonClick()
            {
                Bundle args = new Bundle();
                args.putBoolean(MasterPasswordDialog.ARG_VERIFY_PASSWORD, true);
                args.putString(MasterPasswordDialog.ARG_RECEIVER_FRAGMENT_TAG, getTag());
                args.putString(MasterPasswordDialog.ARG_LABEL, getString(R.string.enter_new_password));
                MasterPasswordDialog mpd = new MasterPasswordDialog();
                mpd.setArguments(args);
                mpd.show(getParentFragmentManager(), MasterPasswordDialog.TAG);
            }
        }));
        commonPropertiesIds.add(getPropertiesView().addProperty(new SwitchPropertyEditor(this, R.string.two_factor_auth, R.string.two_factor_auth_summary)
        {
            @Override
            protected boolean loadValue()
            {
                return _settings.is2FAEnabled();
            }

            @Override
            protected void saveValue(boolean value)
            {
                _settings.set2FAEnabled(value);
                if (value) {
                    try {
                        if (_settings.get2FASecret() == null || _settings.get2FASecret().isEmpty()) {
                            String secret = TwoFactorAuth.generateSecret();
                            _settings.set2FASecret(secret);
                            // Show secret to user
                            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getActivity());
                            builder.setTitle(R.string.two_factor_auth)
                                   .setMessage(getString(R.string.two_factor_auth_secret_label) + "\n\n" + secret)
                                   .setPositiveButton(R.string.ok, null)
                                   .show();
                        }
                    } catch (Settings.InvalidSettingsPassword e) {
                        Logger.log(e);
                    }
                }
            }
        }));
        commonPropertiesIds.add(getPropertiesView().addProperty(new SwitchPropertyEditor(this, R.string.show_previews, 0)
        {
            @Override
            protected boolean loadValue()
            {
                return _settings.showPreviews();
            }

            @Override
            protected void saveValue(boolean value)
            {
                editSettings().putBoolean(SHOW_PREVIEWS, value).commit();
            }
        }));
        commonPropertiesIds.add(getPropertiesView().addProperty(new SwitchPropertyEditor(this, R.string.disable_wide_screen_layouts, R.string.disable_wide_screen_layouts_desc)
        {
            @Override
            protected boolean loadValue()
            {
                return _settings.disableLargeSceenLayouts();
            }

            @Override
            protected void saveValue(boolean value)
            {
                editSettings().putBoolean(DISABLE_WIDE_SCREEN_LAYOUTS, value).commit();
            }
        }));
        commonPropertiesIds.add(getPropertiesView().addProperty(new SwitchPropertyEditor(this, R.string.never_save_history, R.string.never_save_history_desc)
        {
            @Override
            protected boolean loadValue()
            {
                return _settings.neverSaveHistory();
            }

            @Override
            protected void saveValue(boolean value)
            {
                editSettings().putBoolean(NEVER_SAVE_HISTORY, value).commit();
            }
        }));
        commonPropertiesIds.add(getPropertiesView().addProperty(new ChoiceDialogPropertyEditor(this, R.string.internal_image_viewer_mode, R.string.internal_image_viewer_mode_desc, getTag())
        {
            @Override
            protected int loadValue()
            {
                return _settings.getInternalImageViewerMode();
            }

            @Override
            protected void saveValue(int value)
            {
                if (value >= 0)
                    editSettings().putInt(USE_INTERNAL_IMAGE_VIEWER, value).commit();
                else
                    editSettings().remove(USE_INTERNAL_IMAGE_VIEWER).commit();
            }

            @Override
            protected List<String> getEntries()
            {
                String[] modes = getResources().getStringArray(R.array.image_viewer_use_mode);
                return Arrays.asList(modes);
            }
        }));
        commonPropertiesIds.add(getPropertiesView().addProperty(new PathPropertyEditor(this, R.string.temp_data_path, R.string.temp_data_path_desc, getTag())
        {
            @Override
            protected String loadText()
            {
                return _settings.getWorkDir();
            }

            @Override
            protected void saveText(String text)
            {
                if (text.trim().length() > 0)
                {
                    try
                    {
                        Location loc = LocationsManager.getLocationsManager(getActivity()).
                                getLocation(Uri.parse(text));
                        PathUtil.makeFullPath(loc.getCurrentPath());
                        editSettings().putString(WORK_DIR, text).commit();
                    }
                    catch (Exception e)
                    {
                        Logger.showAndLog(getActivity(), e);
                    }
                } else
                    editSettings().remove(WORK_DIR).commit();
            }

            @Override
            protected Intent getSelectPathIntent() throws IOException
            {
                Intent i = FileManagerActivity.getSelectPathIntent(
                        getHost().getContext(),
                        null,
                        false,
                        false,
                        true,
                        true,
                        true,
                        false);
                i.putExtra(FileManagerActivity.EXTRA_ALLOW_BROWSE_DOCUMENT_PROVIDERS,true);
                return i;
            }
        }));
        commonPropertiesIds.add(getPropertiesView().addProperty(new ExtFileManagerPropertyEditor(this)));
        commonPropertiesIds.add(getPropertiesView().addProperty(
                new ChoiceDialogPropertyEditor(this, R.string.signaling_mode, R.string.signaling_mode_desc, getTag())
                {
                    @Override
                    protected int loadValue()
                    {
                        int value = SIGNALING_MODE_HTTP.equals(_settings.getSignalingMode()) ? 1 : 0;
                        getPropertiesView().setPropertyState(R.string.signaling_server_url, value == 1);
                        return value;
                    }

                    @Override
                    protected void saveValue(int value)
                    {
                        editSettings().putString(SIGNALING_MODE, value == 1 ? SIGNALING_MODE_HTTP : com.sovworks.eds.android.settings.UserSettingsCommon.SIGNALING_MODE_LOCAL).commit();
                        getPropertiesView().setPropertyState(R.string.signaling_server_url, value == 1);
                        restartWebRtcService();
                    }

                    @Override
                    protected List<String> getEntries()
                    {
                        return Arrays.asList(
                                getString(R.string.signaling_mode_local),
                                getString(R.string.signaling_mode_http)
                        );
                    }
                }));
        commonPropertiesIds.add(getPropertiesView().addProperty(
                new TextPropertyEditor(this, R.string.signaling_server_url, R.string.signaling_server_url_desc, getTag())
                {
                    @Override
                    protected String loadText()
                    {
                        return _settings.getSignalingServerUrl();
                    }

                    @Override
                    protected void saveText(String text)
                    {
                        if (text != null && text.trim().length() > 0)
                            editSettings().putString(SIGNALING_SERVER_URL, text.trim()).commit();
                        else
                            editSettings().remove(SIGNALING_SERVER_URL).commit();
                        restartWebRtcService();
                    }
                }));
        commonPropertiesIds.add(getPropertiesView().addProperty(new IntPropertyEditor(this, R.string.max_temporary_file_size, R.string.max_temporary_file_size_desc, getTag())
        {
            @Override
            protected int getDialogViewResId()
            {
                return R.layout.settings_edit_num_lim4;
            }

            @Override
            protected int loadValue()
            {
                return _settings.getMaxTempFileSize();
            }

            @Override
            protected void saveValue(int value)
            {
                if (value >= 0)
                    editSettings().putInt(MAX_FILE_SIZE_TO_OPEN, value).commit();
                else
                    editSettings().remove(MAX_FILE_SIZE_TO_OPEN).commit();
            }
        }));
        commonPropertiesIds.add(getPropertiesView().addProperty(new SwitchPropertyEditor(this, R.string.overwrite_temp_files_with_random_data, R.string.overwrite_temp_files_with_random_data_desc)
        {
            @Override
            protected boolean loadValue()
            {
                return _settings.wipeTempFiles();
            }

            @Override
            protected void saveValue(boolean value)
            {
                editSettings().putBoolean(WIPE_TEMP_FILES, value).commit();
            }
        }));
        commonPropertiesIds.add(getPropertiesView().addProperty(new MultilineTextPropertyEditor(this, R.string.extension_mime_override, R.string.extension_mime_override_desc, getTag())
        {
            @Override
            protected String loadText()
            {
                return _settings.getExtensionsMimeMapString();
            }

            @Override
            protected void saveText(String text)
            {
                if (text != null)
                    editSettings().putString(EXTENSIONS_MIME, text).commit();
                else
                    editSettings().remove(EXTENSIONS_MIME).commit();
            }
        }));
        commonPropertiesIds.add(getPropertiesView().addProperty(new SwitchPropertyEditor(this, R.string.debug_log, 0)
        {
            @Override
            protected boolean loadValue()
            {
                return !_settings.disableDebugLog();
            }

            @Override
            protected void saveValue(final boolean value)
            {
                editSettings().putBoolean(DISABLE_DEBUG_LOG, !value).commit();
                if (!value)
                {
                    Logger.closeLogger();
                    Logger.disableLog(true);
                }
                else
                    try
                    {
                        Logger.disableLog(false);
                        Logger.initLogger();
                    }
                    catch (IOException e)
                    {
                        Logger.showErrorMessage(getContext(), e);
                    }
            }
        }));
        commonPropertiesIds.add(getPropertiesView().addProperty(new SwitchPropertyEditor(this, R.string.disable_modified_files_backup, 0)
        {
            @Override
            protected boolean loadValue()
            {
                return _settings.disableModifiedFilesBackup();
            }

            @Override
            protected void saveValue(final boolean value)
            {
                editSettings().putBoolean(DISABLE_MODIFIED_FILES_BACKUP, value).commit();
            }
        }));
        commonPropertiesIds.add(getPropertiesView().addProperty(new SwitchPropertyEditor(this, R.string.hide_eds_screen_from_other_apps, 0)
        {
            @Override
            protected boolean loadValue()
            {
                return _settings.isFlagSecureEnabled();
            }

            @Override
            protected void saveValue(final boolean value)
            {
                editSettings().putBoolean(IS_FLAG_SECURE_ENABLED, value).commit();
            }
        }));
        commonPropertiesIds.add(getPropertiesView().addProperty(new SwitchPropertyEditor(this, R.string.always_force_close_containers, R.string.always_force_close_containers_desc)
        {
            @Override
            protected void saveValue(boolean value)
            {
                editSettings().putBoolean(UserSettings.FORCE_UNMOUNT, value).commit();
            }

            @Override
            protected boolean loadValue()
            {
                return _settings.alwaysForceClose();
            }
        }));
        commonPropertiesIds.add(getPropertiesView().addProperty(new SwitchPropertyEditor(this, R.string.dont_use_content_provider, R.string.dont_use_content_provider_desc)
        {
            @Override
            protected boolean loadValue()
            {
                boolean value = _settings.dontUseContentProvider();
                getPropertiesView().setPropertyState(
                        R.string.force_temp_files,
                        getPropertiesView().isPropertyEnabled(getId()) && !value
                );
                return value;
            }

            @Override
            protected void saveValue(final boolean value)
            {
                editSettings().putBoolean(DONT_USE_CONTENT_PROVIDER, value).commit();
                getPropertiesView().setPropertyState(R.string.force_temp_files, !value);
            }
        }));
        commonPropertiesIds.add(getPropertiesView().addProperty(new SwitchPropertyEditor(this, R.string.force_temp_files, R.string.force_temp_files_desc)
        {
            @Override
            protected boolean loadValue()
            {
                return _settings.forceTempFiles();
            }

            @Override
            protected void saveValue(final boolean value)
            {
                editSettings().putBoolean(FORCE_TEMP_FILES, value).commit();
            }
        }));
        commonPropertiesIds.add(getPropertiesView().addProperty(new InstallExFatModulePropertyEditor(this)));
        commonPropertiesIds.add(getPropertiesView().addProperty(new ButtonPropertyEditor(this, R.string.export_identity_backup, 0, R.string.export_identity_backup)
        {
            @Override
            protected void onButtonClick()
            {
                showBackupPasswordDialog(true);
            }
        }));
        commonPropertiesIds.add(getPropertiesView().addProperty(new ButtonPropertyEditor(this, R.string.import_identity_backup, 0, R.string.import_identity_backup)
        {
            @Override
            protected void onButtonClick()
            {
                showBackupPasswordDialog(false);
            }
        }));
    }

    private void showBackupPasswordDialog(final boolean export) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getActivity());
        builder.setTitle(export ? R.string.export_identity_backup : R.string.import_identity_backup);
        final android.widget.EditText input = new android.widget.EditText(getActivity());
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint(R.string.backup_password);
        builder.setView(input);
        builder.setPositiveButton(R.string.ok, (dialog, which) -> {
            String password = input.getText().toString();
            if (export) {
                String backup = IdentityManager.INSTANCE.exportIdentity(getActivity(), password.getBytes());
                if (backup != null) {
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getActivity().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                    android.content.ClipData clip = android.content.ClipData.newPlainText("Ananta Identity Backup", backup);
                    clipboard.setPrimaryClip(clip);
                    android.widget.Toast.makeText(getActivity(), R.string.identity_backup_exported, android.widget.Toast.LENGTH_LONG).show();
                } else {
                    android.widget.Toast.makeText(getActivity(), R.string.identity_backup_failed, android.widget.Toast.LENGTH_LONG).show();
                }
            } else {
                showImportBackupDialog(password);
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private void showImportBackupDialog(final String password) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.import_identity_backup);
        final android.widget.EditText input = new android.widget.EditText(getActivity());
        input.setHint("Backup String hier einfügen");
        builder.setView(input);
        builder.setPositiveButton(R.string.ok, (dialog, which) -> {
            String backup = input.getText().toString();
            if (IdentityManager.INSTANCE.importIdentity(getActivity(), backup, password.getBytes())) {
                android.widget.Toast.makeText(getActivity(), R.string.identity_backup_imported, android.widget.Toast.LENGTH_LONG).show();
            } else {
                android.widget.Toast.makeText(getActivity(), R.string.identity_backup_failed, android.widget.Toast.LENGTH_LONG).show();
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    protected void enableProperties(Iterable<Integer> propIds, boolean enable)
    {
        getPropertiesView().setPropertiesState(propIds, enable);
        getPropertiesView().loadProperties();
    }

    private void restartWebRtcService()
    {
        final android.content.Context appContext = getActivity().getApplicationContext();
        new Thread(() -> WebRtcService.initialize(appContext, _settings),
                "WebRtcServiceInit").start();
    }

}

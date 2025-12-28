package com.sovworks.eds.android.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.sovworks.eds.android.R;
import com.sovworks.eds.android.settings.UserSettings;
import com.sovworks.eds.crypto.TwoFactorAuth;
import com.sovworks.eds.settings.Settings;

public class TwoFactorAuthDialog extends DialogFragment {

    public interface TwoFactorAuthListener {
        void on2FASuccess();
        void on2FACancel();
    }

    private TwoFactorAuthListener listener;

    public void setListener(TwoFactorAuthListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_2fa, null);
        final EditText input = view.findViewById(R.id.two_factor_code);

        builder.setView(view)
                .setTitle(R.string.two_factor_auth)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        verify(input.getText().toString());
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        if (listener != null) listener.on2FACancel();
                        TwoFactorAuthDialog.this.getDialog().cancel();
                    }
                });
        return builder.create();
    }

    private void verify(String code) {
        try {
            UserSettings settings = UserSettings.getSettings(getActivity());
            String secret = settings.get2FASecret();
            if (TwoFactorAuth.verifyCode(secret, code)) {
                if (listener != null) listener.on2FASuccess();
            } else {
                Toast.makeText(getActivity(), R.string.invalid_2fa_code, Toast.LENGTH_SHORT).show();
                if (listener != null) listener.on2FACancel();
            }
        } catch (Settings.InvalidSettingsPassword e) {
            Toast.makeText(getActivity(), R.string.error_accessing_2fa, Toast.LENGTH_SHORT).show();
            if (listener != null) listener.on2FACancel();
        }
    }
}

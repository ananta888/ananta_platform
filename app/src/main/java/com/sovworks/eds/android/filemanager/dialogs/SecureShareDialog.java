package com.sovworks.eds.android.filemanager.dialogs;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.appcompat.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;

import com.sovworks.eds.android.R;
import com.sovworks.eds.android.trust.TrustStore;
import com.sovworks.eds.android.trust.TrustedKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SecureShareDialog extends DialogFragment {
    public static final String TAG = "SecureShareDialog";

    public interface Receiver {
        void onSecureShareConfirmed(String passphrase, List<String> publicKeys);
    }

    public static void showDialog(FragmentManager fragmentManager, String tag) {
        SecureShareDialog dialog = new SecureShareDialog();
        dialog.show(fragmentManager, tag);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.secure_share_dialog, null);

        final RadioGroup modeGroup = view.findViewById(R.id.encryption_mode_group);
        final EditText passphraseEdit = view.findViewById(R.id.passphrase_edit);
        final Spinner keysSpinner = view.findViewById(R.id.keys_spinner);
        final View manageKeysButton = view.findViewById(R.id.manage_keys_button);

        final TrustStore trustStore = TrustStore.getInstance(getActivity());
        final Map<String, TrustedKey> allKeys = trustStore.getAllKeys();
        final List<String> keyFingerprints = new ArrayList<>(allKeys.keySet());
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_spinner_item, keyFingerprints);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        keysSpinner.setAdapter(adapter);

        modeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.radio_passphrase) {
                    passphraseEdit.setVisibility(View.VISIBLE);
                    keysSpinner.setVisibility(View.GONE);
                    manageKeysButton.setVisibility(View.GONE);
                } else {
                    passphraseEdit.setVisibility(View.GONE);
                    keysSpinner.setVisibility(View.VISIBLE);
                    manageKeysButton.setVisibility(View.VISIBLE);
                }
            }
        });

        builder.setView(view)
                .setTitle(R.string.secure_share_age_title)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        Receiver receiver = null;
                        if (getParentFragment() instanceof Receiver) {
                            receiver = (Receiver) getParentFragment();
                        } else if (getActivity() instanceof Receiver) {
                            receiver = (Receiver) getActivity();
                        }

                        if (receiver != null) {
                            if (modeGroup.getCheckedRadioButtonId() == R.id.radio_passphrase) {
                                receiver.onSecureShareConfirmed(passphraseEdit.getText().toString(), null);
                            } else {
                                String selectedFingerprint = (String) keysSpinner.getSelectedItem();
                                if (selectedFingerprint != null) {
                                    List<String> keys = new ArrayList<>();
                                    keys.add(allKeys.get(selectedFingerprint).getPublicKey());
                                    receiver.onSecureShareConfirmed(null, keys);
                                }
                            }
                        }
                    }
                })
                .setNegativeButton(R.string.no, null);

        return builder.create();
    }
}


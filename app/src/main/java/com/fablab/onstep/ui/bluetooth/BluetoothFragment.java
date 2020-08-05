package com.fablab.onstep.ui.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.fablab.onstep.MainActivity;
import com.fablab.onstep.R;
import com.fablab.onstep.ui.options.OptionsFragment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class BluetoothFragment extends Fragment {
    private View root;
    private static boolean connected = false;

    private ArrayList<BluetoothDevice> pairedDevices;
    private AlertDialog animationDialog;
    BluetoothSocket bluetoothSocket;

    private static OutputStream outputStream;
    private Thread connectionThread;

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_bluetooth, container, false);

        Button bluetoothControlButton = root.findViewById(R.id.bluetoothControlButton);
        bluetoothControlButton.setOnClickListener((v) -> setupDiscovery());

        this.root = root;

        return root;
    }

    private void setupDiscovery() {
        MainActivity.applicationLogs.add("Starting discovery...");
        try {
            BluetoothAdapter adapter = getAdapter();
            if (adapter != null) {
                pairedDevices = getPairedDevices(adapter);
                createBroadcastReceiver(adapter);
            }
        } catch (Exception e) {
            MainActivity.createAlert("We encountered an error, please make sure that your Bluetooth is enabled. Cause: " + e.getMessage(), root, false);
            MainActivity.applicationLogs.add("Exception while setting up the discovery: " + e.getMessage() + Arrays.toString(e.getStackTrace()));
        }
    }

    private void createBroadcastReceiver(BluetoothAdapter adapter) {
        try {
            BroadcastReceiver receiver = registerListener();
            startDiscovery(adapter, receiver);
        } catch (Exception e) {
            if (OptionsFragment.getPreferencesBoolean("debug", requireContext())) {
                String message = "Stack: " + Arrays.toString(e.getStackTrace()) + " **Cause**: " + e.getCause() + "**Message**: " + e.getMessage();
                MainActivity.applicationLogs.add(message);
            }

            MainActivity.createOverlayAlert("Error", "There was an error while starting the discovery, you can try to restart the app.", requireContext());
            MainActivity.applicationLogs.add("Error while starting the discovery: cause " + e.getMessage());
        }
    }

    private void startDiscovery(BluetoothAdapter adapter, BroadcastReceiver broadcastReceiver) {
        if (adapter.isDiscovering()) {
            //restart discovery if it's already running
            adapter.cancelDiscovery();
            MainActivity.applicationLogs.add("Discovery canceled as is was already running (shouldn't have happened)");
        }
        adapter.startDiscovery();

        Button bluetoothControlButton = root.findViewById(R.id.bluetoothControlButton);
        bluetoothControlButton.setText(getString(R.string.stop_discovery));
        bluetoothControlButton.setOnClickListener((v) -> stopDiscovery(adapter, broadcastReceiver));
    }

    private void stopDiscovery(BluetoothAdapter adapter, BroadcastReceiver broadcastReceiver) {
        MainActivity.applicationLogs.add("Stopping discovery...");

        if (adapter != null)
            adapter.cancelDiscovery();
        else {
            MainActivity.createAlert("Couldn't cancel discovery: adapter is null.", root, true);
            return;
        }

        if (connected) {
            MainActivity.createCriticalErrorAlert("Critical error", "A critical error has occurred, click on restart to restart the app.", requireContext());
            MainActivity.applicationLogs.add("Connection state update missing! Requesting restart...");
        }

        Button bluetoothControlButton = root.findViewById(R.id.bluetoothControlButton);
        bluetoothControlButton.setText(R.string.scan);
        bluetoothControlButton.setOnClickListener((v) -> setupDiscovery());

        if (getContext() != null)
            getContext().unregisterReceiver(broadcastReceiver);
        else contextNotFound();
    }

    private BluetoothAdapter getAdapter() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            // If the adapter is null it means that the device does not support BluetoothDiscovery
            new Handler(Looper.getMainLooper()).post(() -> MainActivity.createAlert("Your device doesn't support Bluetooth therefore you won't be able to use it.", root, false));

            MainActivity.applicationLogs.add("This device doesn't support Bluetooth.");
            return null;
        } else {
            if (!bluetoothAdapter.isEnabled()) {
                MainActivity.applicationLogs.add("Adapter is offline, requesting to turn on...");

                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 1);

                return null; //cancel the connection process
            }

            MainActivity.applicationLogs.add("Adapter is ready.");
            return bluetoothAdapter;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1 && resultCode == -1) {
            setupDiscovery();
        } else if (resultCode == 0) {
            MainActivity.applicationLogs.add("User denied permission to turn on Bluetooth.");
            MainActivity.createAlert("You need to turn on Bluetooth in order to start scanning.", root, false);
            //button isn't unlocked if we don't grant permission.
            Button bluetoothControlButton = root.findViewById(R.id.bluetoothControlButton);
            bluetoothControlButton.setText(R.string.scan);
            bluetoothControlButton.setOnClickListener((v) -> setupDiscovery());
        } else {
            MainActivity.applicationLogs.add("Unexpected resultCode for onActivityResult().");
            MainActivity.createOverlayAlert("Alert", "We encountered an unexpected error while filtering out some requests. No restart is needed, but some functions may not work.", requireContext());
            //we're resetting the button to not risk.
            Button bluetoothControlButton = root.findViewById(R.id.bluetoothControlButton);
            bluetoothControlButton.setText(R.string.scan);
            bluetoothControlButton.setOnClickListener((v) -> setupDiscovery());
        }
    }

    private BroadcastReceiver registerListener() {
        ArrayList<String> MACAddresses = new ArrayList<>();
        MainActivity.applicationLogs.add("Registering listener...");

        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                    if (device == null) {
                        MainActivity.applicationLogs.add("Null device, returning.");
                        return;
                    }

                    if (MACAddresses.contains(device.getAddress())) {
                        MainActivity.applicationLogs.add("Device with MAC address " + device.getAddress() + " is duplicate.");
                    } else {
                        MainActivity.applicationLogs.add("New device, name: " + device.getName() + " MACAddr: " + device.getAddress());
                        MACAddresses.add(device.getAddress());

                        View v = View.inflate(requireContext(), R.layout.bluetooth_device, null); //inflating from a layoutInflater requires parent view as parameter but it seems not fixable... https://stackoverflow.com/questions/24832497/avoid-passing-null-as-the-view-root-need-to-resolve-layout-parameters-on-the-in

                        TextView deviceNameTextView = v.findViewById(R.id.deviceName);
                        deviceNameTextView.setText(device.getName());

                        TextView deviceMACAddressTextView = v.findViewById(R.id.deviceMACAddress);
                        deviceMACAddressTextView.setText(device.getAddress());

                        v.setOnClickListener((buttonView) -> connect(device));

                        ViewGroup containerLayout = requireView().findViewById(R.id.devicesLayout);
                        containerLayout.post(() -> containerLayout.addView(v, 0, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)));
                    }
                }
            }
        };
        // Register the broadcast receiver
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        if (getContext() != null) getContext().registerReceiver(broadcastReceiver, filter);
        else contextNotFound();

        return broadcastReceiver;
    }

    private ArrayList<BluetoothDevice> getPairedDevices(BluetoothAdapter adapter) {
        Set<BluetoothDevice> pairedDevices =  adapter.getBondedDevices();

        MainActivity.applicationLogs.add(pairedDevices.size() + " paired devices found.");

        return new ArrayList<>(pairedDevices);
    }

    //-----------connect--------------

    private void startConnectionAnimation() {
        MainActivity.applicationLogs.add("Starting connection animation...");

        animationDialog = new AlertDialog.Builder(requireContext(), R.style.DarkTheme_AnimationDialog).setCancelable(false).setTitle("Connecting").create();

        ImageView animation = new ImageView(requireContext());

        final float scale = requireContext().getResources().getDisplayMetrics().density;
        int pixelsW = (int) (220 * scale + 0.5f);

        animation.setLayoutParams(new ConstraintLayout.LayoutParams(
                pixelsW, pixelsW
        ));

        int animationId = View.generateViewId();
        animation.setId(animationId);

        ConstraintLayout animationLayout = new ConstraintLayout(requireContext());
        animationLayout.addView(animation);

        animationDialog.show();
        try {
            //we need to prevent the dialog from dimming the view outside of its window, otherwise the gif background won't correspond to the current one.
            WindowManager.LayoutParams lp = Objects.requireNonNull(animationDialog.getWindow()).getAttributes();
            lp.dimAmount=0.0f;
            animationDialog.getWindow().setAttributes(lp);
        } catch (NullPointerException e) {
            MainActivity.createAlert("Failed to set some settings on the loading animation", root, false);
        }
        animationDialog.setContentView(animationLayout, new ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.MATCH_PARENT));

        Glide.with(requireContext()).load((OptionsFragment.getPreferencesBoolean("DarkTheme", requireContext())) ? R.drawable.connecting_dark : R.drawable.connecting_light).into((ImageView) animationLayout.findViewById(animationId)); //cast is necessary due to Target<Drawable> being ambiguous
    }

    private void stopConnectionAnimation() {
        MainActivity.applicationLogs.add("Stopping connection animation...");

        animationDialog.dismiss();
    }

    private void connect(BluetoothDevice device) {
        MainActivity.applicationLogs.add("Starting connection for: " + device.getAddress());
        startConnectionAnimation();


        if (!pairedDevices.contains(device)) {
            MainActivity.createAlert("You need to pair to this device first.", root, true);
            stopConnectionAnimation();
            return;
        }

        connectionThread = new Thread() {
            @Override
            public void run() {
                super.run();

                final UUID genericConnectionUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
                try {
                    bluetoothSocket = device.createRfcommSocketToServiceRecord(genericConnectionUUID);
                } catch (IOException e) {
                    new Handler(Looper.getMainLooper()).post(() -> MainActivity.createAlert("Socket creation failed ):", root, false));
                    MainActivity.applicationLogs.add("Socket creation failed: " + e.getMessage());
                    return;
                }

                try {
                    bluetoothSocket.connect();

                    connected = true;

                    Button bluetoothControlButton = root.findViewById(R.id.bluetoothControlButton);
                    bluetoothControlButton.setText(R.string.disconnect);
                    bluetoothControlButton.setOnClickListener((v) -> disconnectBluetooth());

                    new Handler(Looper.getMainLooper()).post(() -> MainActivity.createAlert("Connection successful.", root, false));
                    stopConnectionAnimation();

                    new Thread() {
                        @Override
                        public void run() {
                            try {
                                outputStream = bluetoothSocket.getOutputStream();
                                InputStream in = bluetoothSocket.getInputStream();

                                BufferedReader br = new BufferedReader(new InputStreamReader(in));

                                String message;
                                while ((message = br.readLine()) != null) {
                                    MainActivity.applicationLogs.add("Received: " + message);
                                }
                            } catch (IOException e) {
                                MainActivity.createAlert("Unable to read from InputStream: disconnected.", root, false);
                                MainActivity.applicationLogs.add("Connection error, cause: " + e.getMessage());

                                outputStream = null;
                                connected = false;
                            }
                        }
                    }.start();
                } catch (Exception e) {
                    stopConnectionAnimation();
                    disconnectBluetooth();

                    new Handler(Looper.getMainLooper()).post(() -> MainActivity.createAlert("Connection failed.", root, false));
                    MainActivity.applicationLogs.add("Connection failed.\nCause: " + e.getMessage());
                    connected = false;
                }
            }


        };

        connectionThread.start();
    }

    private void disconnectBluetooth() {
        MainActivity.applicationLogs.add("Disconnecting...");

        try {
            bluetoothSocket.close();
            connected = false;
        } catch (IOException e) {
            new Handler(Looper.getMainLooper()).post(() -> MainActivity.createAlert("Error while closing the client socket: disconnected.", root, false));
            if (OptionsFragment.getPreferencesBoolean("debug", getContext()))
                MainActivity.applicationLogs.add("Couldn't close the client socket. Cause: " + e.getCause() + " \n Stack trace: " + Arrays.toString(e.getStackTrace()));
        } catch (NullPointerException e) {
            new Handler(Looper.getMainLooper()).post(() -> MainActivity.createOverlayAlert("Error", "Error while closing the client socket.  Cause: " + e.getMessage(), requireContext()));
            MainActivity.applicationLogs.add("Couldn't close the client socket. Cause: " + e.getCause() + " \n Stack trace: " + Arrays.toString(e.getStackTrace()));
        }
    }

    public static void sendData(View callingView, byte[] command) {
        if (outputStream != null) {
            try {
                outputStream.write(command);
                outputStream.flush();
            } catch (IOException e) {
                String msg = "Couldn't write command. Cause: " + e.getMessage();
                MainActivity.createAlert(msg, callingView, false);
            }
        } else {
            MainActivity.createAlert("Not connected!", callingView, true);
        }
    }

    public static void openCustomCommandDialog(View view, Context applicationContext) {
        boolean DarkTheme = OptionsFragment.getPreferencesBoolean("DarkTheme", applicationContext);
        AlertDialog.Builder dialog = new AlertDialog.Builder(applicationContext, DarkTheme ? R.style.DialogTheme : R.style.Theme_AppCompat_Light_Dialog);
        dialog.setTitle("Command parameters");
        dialog.setMessage("Type in the command");

        LinearLayout layout = new LinearLayout(applicationContext);
        layout.setOrientation(LinearLayout.VERTICAL); //without this you can only put one view at once
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(30, 10, 30, 10);
        layout.setLayoutParams(lp);

        EditText commandInput = new EditText(applicationContext);
        if (DarkTheme) commandInput.setTextColor(Color.WHITE);
        commandInput.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        commandInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL); //input is floating point

        layout.addView(commandInput);
        dialog.setView(layout);
        dialog.setPositiveButton("Done", (dialog1, which) -> {
            byte[] command = new byte[commandInput.getText().toString().split(" ").length];
            int ptr = 0;
            for (String currentByte : commandInput.getText().toString().split(" ")) {
                command[ptr] = (byte) Integer.parseInt(currentByte);
                ptr++;
            }

            try {
                sendData(view, command);
            } catch (NumberFormatException e) {
                MainActivity.createAlert("Please insert a valid number", view, false);
            }
        });
        AlertDialog alertDialog = dialog.create();
        alertDialog.show();
    }

    private void contextNotFound() {
        MainActivity.createAlert("There was an error while updating the countdown. You should restart the app.", root, false);
    }

    @Override
    public void onPause() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();

        if (adapter != null) adapter.cancelDiscovery();
        else {
            MainActivity.createOverlayAlert("Error", "We had an error cancelling the device discovery. It is recommended to restart the app.", getContext());
            MainActivity.applicationLogs.add("Adapter is null while cancelling discovery.");
        }
        super.onPause();
    }

    @Override
    public void onStop() {
        MainActivity.applicationLogs.add("Stopping animationDialog.");

        if (animationDialog != null && animationDialog.isShowing()) {
            animationDialog.dismiss();
            //we need to dismiss any dialog when the phone is rotated, otherwise an exception will be thrown. See https://stackoverflow.com/questions/2224676/android-view-not-attached-to-window-manager
        }
        if (connectionThread != null && connectionThread.isAlive()) {
            connectionThread.interrupt();
            connectionThread = null;
        }
        super.onStop();
    }
}
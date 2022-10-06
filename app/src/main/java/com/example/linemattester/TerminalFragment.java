package com.example.linemattester;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TerminalFragment extends Fragment implements ServiceConnection, SerialListener {

    private enum Connected { False, Pending, True }

    private String deviceAddress;
    private SerialService service;

    private TextView receiveText;
    private TextView sendText;
    private com.example.linemattester.TextUtil.HexWatcher hexWatcher;

    private Connected connected = Connected.False;
    private boolean initialStart = true;
    private boolean hexEnabled = false;
    private boolean pendingNewline = false;
    private String newline = com.example.linemattester.TextUtil.newline_crlf;

    private GridView gridView;

    private static final String TAG = "NordicBleTest";
    String check_stsus_Cmd = "41 54 43 4D 08 00 00 00 00 00 00 00 00 00 3E 31" ;    //BTCM_CHECK_STATUS_REQ(ATCM)
    String set_rate_Cmd = "41 54 43 46 02 00 11 11 00 00" ;   //realtime = 100hz(17) store=100HZ(17)
    String start_real_time_Cmd = "41 54 53 4D 02 00 01 64 00 00 " ;
    String stop_real_time_Cmd = "41 54 45 4D" ;


    // GridView
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////
    List<String> ItemsList;
    String selectedItem;
    TextView GridViewItems,BackSelectedItem;
    int backposition = -1;
    String[] itemsName = new String[1200];

    int reflash_ayy;
    byte[]  yg_data_byte =new byte[1200];
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /*
     * Lifecycle
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);

        deviceAddress = getArguments().getString("device");

    }

    @Override
    public void onDestroy() {
        if (connected != Connected.False)
            disconnect();
        getActivity().stopService(new Intent(getActivity(), SerialService.class));
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if(service != null)
            service.attach(this);
        else
            getActivity().startService(new Intent(getActivity(), SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    @Override
    public void onStop() {
        if(service != null && !getActivity().isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @SuppressWarnings("deprecation") // onAttach(context) was added with API 23. onAttach(activity) works for all API versions
    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        getActivity().bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        try { getActivity().unbindService(this); } catch(Exception ignored) {}
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        if(initialStart && service != null) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        service.attach(this);
        if(initialStart && isResumed()) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }

    /*
     * UI
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_terminal, container, false);
        receiveText = view.findViewById(R.id.receive_text);                          // TextView performance decreases with number of spans
        receiveText.setTextColor(getResources().getColor(R.color.colorRecieveText)); // set as default color to reduce number of spans
        receiveText.setMovementMethod(ScrollingMovementMethod.getInstance());

        sendText = view.findViewById(R.id.send_text);
        hexWatcher = new com.example.linemattester.TextUtil.HexWatcher(sendText);
        hexWatcher.enable(hexEnabled);
        sendText.addTextChangedListener(hexWatcher);
        sendText.setHint(hexEnabled ? "HEX mode" : "");

        gridView = view.findViewById(R.id.gridView);
        Context mContext = this.getActivity();

        ItemsList = new ArrayList<String>(Arrays.asList(itemsName));

        gridView.setAdapter(new TextAdapter(mContext));

        View sendBtn = view.findViewById(R.id.send_btn);
        //sendBtn.setOnClickListener(v -> send(sendText.getText().toString()));
        //Log.i(TAG, sendText.getText().toString());
        sendBtn.setOnClickListener (new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                send(sendText.getText().toString()) ;
                Log.i(TAG, sendText.getText().toString());
            }
        }) ;

        View checkStsusBtn = view.findViewById(R.id.check_stsus_btn);
        //checkStsusBtn.setOnClickListener(v -> send(check_stsus_Cmd));
        checkStsusBtn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                hexEnabled = true;
                send(check_stsus_Cmd) ;
                Log.i(TAG, check_stsus_Cmd);

            }
        }) ;

        View setRateBtn = view.findViewById(R.id.set_rate_btn);
        setRateBtn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                hexEnabled = true;
                send(set_rate_Cmd) ;
                Log.i(TAG, set_rate_Cmd);
            }
        }) ;

        View startRealRimeBtn = view.findViewById(R.id.start_real_time_btn);
        startRealRimeBtn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                hexEnabled = true;
                send(start_real_time_Cmd) ;
                Log.i(TAG, start_real_time_Cmd);
            }
        }) ;

        View stopRealRimeBtn = view.findViewById(R.id.stop_real_time_btn);
        stopRealRimeBtn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                hexEnabled = true;
                send(stop_real_time_Cmd) ;
                Log.i(TAG, stop_real_time_Cmd);
            }
        }) ;


        return view;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_terminal, menu);
        menu.findItem(R.id.hex).setChecked(hexEnabled);
    }



    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.clear) {
            receiveText.setText("");
            return true;
        } else if (id == R.id.newline) {
            String[] newlineNames = getResources().getStringArray(R.array.newline_names);
            String[] newlineValues = getResources().getStringArray(R.array.newline_values);
            int pos = Arrays.asList(newlineValues).indexOf(newline);
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("Newline");
            builder.setSingleChoiceItems(newlineNames, pos, (dialog, item1) -> {
                newline = newlineValues[item1];
                dialog.dismiss();
            });
            builder.create().show();
            return true;
        } else if (id == R.id.hex) {
            hexEnabled = !hexEnabled;
            sendText.setText("");
            hexWatcher.enable(hexEnabled);
            sendText.setHint(hexEnabled ? "HEX mode" : "");
            item.setChecked(hexEnabled);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    /*
     * Serial + UI
     */
    private void connect() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            status("connecting...");
            connected = Connected.Pending;
            com.example.linemattester.SerialSocket socket = new com.example.linemattester.SerialSocket(getActivity().getApplicationContext(), device);
            service.connect(socket);
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void disconnect() {
        connected = Connected.False;
        service.disconnect();
    }

    private void send(String str) {
        if(connected != Connected.True) {
            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String msg;
            byte[] data;
            if(hexEnabled) {
                StringBuilder sb = new StringBuilder();
                com.example.linemattester.TextUtil.toHexString(sb, com.example.linemattester.TextUtil.fromHexString(str));
                com.example.linemattester.TextUtil.toHexString(sb, newline.getBytes());
                msg = sb.toString();
                data = com.example.linemattester.TextUtil.fromHexString(msg);
            } else {
                msg = str;
                data = (str + newline).getBytes();
            }
            SpannableStringBuilder spn = new SpannableStringBuilder(msg + '\n');
            spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            receiveText.append(spn);
            service.write(data);
        } catch (Exception e) {
            onSerialIoError(e);
        }
    }

    int length , real_time_data_length = 300,data_position , ck_position_1, ck_position_2;
    byte[] data_byte = new byte[real_time_data_length];
    private void receive(byte[] data) {

        if(hexEnabled) {
            receiveText.append(com.example.linemattester.TextUtil.toHexString(data) + '\n');
            Log.i(TAG, "Receive Date : " + com.example.linemattester.TextUtil.toHexString(data));

            if(data_position != 0){
                int index , i ,j;
                int data_length = data.length;
                for(index=0;index < data_length - 4;index++){
                    data_byte[data_position + index + 1] = data[index];
                }
                Log.i(TAG, "Realtime data : " + com.example.linemattester.TextUtil.toHexString(data_byte));

                for(i=0;i < 300;i++) {
                    for (j = 0; j < 4; j++) {
                        yg_data_byte[(i * 4) + j] = (byte) (data_byte[i] >> (j * 2) & 0x03);
                    }
                }


                //pad 7_ 1///////////////////////////////////////////////////////////////////////////////////
                for(i=0;i < 10 ;i++) {
                    for (j = 0; j < 10; j++) {
                        ck_position_1 = 9 - j + i * 20;
                        GridViewItems = (TextView) gridView.getChildAt(ck_position_1);
                        GridViewItems.setSelected(false);

                        if (yg_data_byte[600 + i*10 + j] == 0) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_def_color));
                        } else if (yg_data_byte[600 + i*10 + j] == 1) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_level1_color));
                        } else if (yg_data_byte[600 + i*10 + j] == 2) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_err_color));
                        } else if (yg_data_byte[600 + i*10 + j] == 3) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_level2_color));
                        }

                        ck_position_2 = 190 + j - i * 20;
                        GridViewItems = (TextView) gridView.getChildAt(ck_position_2);
                        GridViewItems.setSelected(false);

                        if (yg_data_byte[i*10 + j] == 0) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_def_color));
                        } else if (yg_data_byte[i*10 + j] == 1) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_level1_color));
                        } else if (yg_data_byte[i*10 + j] == 2) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_err_color));
                        } else if (yg_data_byte[i*10 + j] == 3) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_level2_color));
                        }
                    }
                }

                //pad 8_ 2///////////////////////////////////////////////////////////////////////////////////
                for(i=0;i < 10 ;i++) {
                    for (j = 0; j < 10; j++) {
                        ck_position_1 = 209 - j + i * 20;
                        GridViewItems = (TextView) gridView.getChildAt(ck_position_1);
                        GridViewItems.setSelected(false);

                        if (yg_data_byte[700 + i*10 + j] == 0) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_def_color));
                        } else if (yg_data_byte[700 + i*10 + j] == 1) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_level1_color));
                        } else if (yg_data_byte[700 + i*10 + j] == 2) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_err_color));
                        } else if (yg_data_byte[700 + i*10 + j] == 3) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_level2_color));
                        }

                        ck_position_2 = 390 + j - i * 20;
                        GridViewItems = (TextView) gridView.getChildAt(ck_position_2);
                        GridViewItems.setSelected(false);

                        if (yg_data_byte[100 + i*10 + j] == 0) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_def_color));
                        } else if (yg_data_byte[100 +i*10 + j] == 1) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_level1_color));
                        } else if (yg_data_byte[100 +i*10 + j] == 2) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_err_color));
                        } else if (yg_data_byte[100 +i*10 + j] == 3) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_level2_color));
                        }
                    }
                }

                //pad 9_ 3///////////////////////////////////////////////////////////////////////////////////
                for(i=0;i < 10 ;i++) {
                    for (j = 0; j < 10; j++) {
                        ck_position_1 = 409 - j + i * 20;
                        GridViewItems = (TextView) gridView.getChildAt(ck_position_1);
                        GridViewItems.setSelected(false);

                        if (yg_data_byte[800 + i*10 + j] == 0) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_def_color));
                        } else if (yg_data_byte[800 + i*10 + j] == 1) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_level1_color));
                        } else if (yg_data_byte[800 + i*10 + j] == 2) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_err_color));
                        } else if (yg_data_byte[800 + i*10 + j] == 3) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_level2_color));
                        }

                        ck_position_2 = 590 + j - i * 20;
                        GridViewItems = (TextView) gridView.getChildAt(ck_position_2);
                        GridViewItems.setSelected(false);

                        if (yg_data_byte[200 + i*10 + j] == 0) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_def_color));
                        } else if (yg_data_byte[200 +i*10 + j] == 1) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_level1_color));
                        } else if (yg_data_byte[200 +i*10 + j] == 2) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_err_color));
                        } else if (yg_data_byte[200 +i*10 + j] == 3) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_level2_color));
                        }
                    }
                }

                //pad 10_ 4///////////////////////////////////////////////////////////////////////////////////
                for(i=0;i < 10 ;i++) {
                    for (j = 0; j < 10; j++) {
                        ck_position_1 = 609 - j + i * 20;
                        GridViewItems = (TextView) gridView.getChildAt(ck_position_1);
                        GridViewItems.setSelected(false);

                        if (yg_data_byte[900 + i*10 + j] == 0) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_def_color));
                        } else if (yg_data_byte[900 + i*10 + j] == 1) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_level1_color));
                        } else if (yg_data_byte[900 + i*10 + j] == 2) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_err_color));
                        } else if (yg_data_byte[900 + i*10 + j] == 3) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_level2_color));
                        }

                        ck_position_2 = 790 + j - i * 20;
                        GridViewItems = (TextView) gridView.getChildAt(ck_position_2);
                        GridViewItems.setSelected(false);

                        if (yg_data_byte[300 + i*10 + j] == 0) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_def_color));
                        } else if (yg_data_byte[300 +i*10 + j] == 1) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_level1_color));
                        } else if (yg_data_byte[300 +i*10 + j] == 2) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_err_color));
                        } else if (yg_data_byte[300 +i*10 + j] == 3) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_level2_color));
                        }
                    }
                }

                //pad 11_ 5///////////////////////////////////////////////////////////////////////////////////
                for(i=0;i < 10 ;i++) {
                    for (j = 0; j < 10; j++) {
                        ck_position_1 = 809 - j + i * 20;
                        GridViewItems = (TextView) gridView.getChildAt(ck_position_1);
                        GridViewItems.setSelected(false);

                        if (yg_data_byte[1000 + i*10 + j] == 0) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_def_color));
                        } else if (yg_data_byte[1000 + i*10 + j] == 1) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_level1_color));
                        } else if (yg_data_byte[1000 + i*10 + j] == 2) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_err_color));
                        } else if (yg_data_byte[1000 + i*10 + j] == 3) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_level2_color));
                        }

                        ck_position_2 = 990 + j - i * 20;
                        GridViewItems = (TextView) gridView.getChildAt(ck_position_2);
                        GridViewItems.setSelected(false);

                        if (yg_data_byte[400 + i*10 + j] == 0) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_def_color));
                        } else if (yg_data_byte[400 +i*10 + j] == 1) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_level1_color));
                        } else if (yg_data_byte[400 +i*10 + j] == 2) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_err_color));
                        } else if (yg_data_byte[400 +i*10 + j] == 3) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_level2_color));
                        }
                    }
                }

                //pad 12_ 6///////////////////////////////////////////////////////////////////////////////////
                for(i=0;i < 10 ;i++) {
                    for (j = 0; j < 10; j++) {
                        ck_position_1 = 1009 - j + i * 20;
                        GridViewItems = (TextView) gridView.getChildAt(ck_position_1);
                        GridViewItems.setSelected(false);

                        if (yg_data_byte[1100 + i*10 + j] == 0) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_def_color));
                        } else if (yg_data_byte[1100 + i*10 + j] == 1) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_level1_color));
                        } else if (yg_data_byte[1100 + i*10 + j] == 2) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_err_color));
                        } else if (yg_data_byte[1100 + i*10 + j] == 3) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_level2_color));
                        }

                        ck_position_2 = 1190 + j - i * 20;
                        GridViewItems = (TextView) gridView.getChildAt(ck_position_2);
                        GridViewItems.setSelected(false);

                        if (yg_data_byte[500 + i*10 + j] == 0) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_def_color));
                        } else if (yg_data_byte[500 +i*10 + j] == 1) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_level1_color));
                        } else if (yg_data_byte[500 +i*10 + j] == 2) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_err_color));
                        } else if (yg_data_byte[500 +i*10 + j] == 3) {
                            GridViewItems.setBackgroundColor(getResources().getColor(R.color.sensor_level2_color));
                        }
                    }
                }

                data_position = 0;
            }

            if(((char)data[0] == 'a') & ((char)data[1] == 't') & ((char)data[2] == 'y') & ((char)data[3] == 't')){
                length = (int)data[5] * 256  + (int)(data[4]);
                int index;
                int data_length = data.length;

                byte[] timeCnt = new byte[4];
                timeCnt[0] = data[7];
                timeCnt[1] = data[8];
                timeCnt[2] = data[9];
                timeCnt[3] = data[10];

                for(index=0;index < (data_length - 12);index++){
                    data_byte[index] = data[index + 11];
                }
                data_position = index;
                Log.i(TAG, "STX : " + (char)data[0] + (char)data[1] + (char)data[2] + (char)data[3]);
                Log.i(TAG, "Length : " + length);
                Log.i(TAG, "Format : " + data[6]);
                Log.i(TAG, "timeCnt : " + com.example.linemattester.TextUtil.toHexString(timeCnt));

            }



        } else {
            String msg = new String(data);
            if(newline.equals(com.example.linemattester.TextUtil.newline_crlf) && msg.length() > 0) {
                // don't show CR as ^M if directly before LF
                msg = msg.replace(com.example.linemattester.TextUtil.newline_crlf, com.example.linemattester.TextUtil.newline_lf);
                // special handling if CR and LF come in separate fragments
                if (pendingNewline && msg.charAt(0) == '\n') {
                    Editable edt = receiveText.getEditableText();
                    if (edt != null && edt.length() > 1)
                        edt.replace(edt.length() - 2, edt.length(), "");
                }
                pendingNewline = msg.charAt(msg.length() - 1) == '\r';
            }
            receiveText.append(com.example.linemattester.TextUtil.toCaretString(msg, newline.length() != 0));
        }
    }

    private void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str + '\n');
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        receiveText.append(spn);
    }

    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        status("connected");
        connected = Connected.True;
    }

    @Override
    public void onSerialConnectError(Exception e) {
        status("connection failed: " + e.getMessage());
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
        receive(data);
    }

    @Override
    public void onSerialIoError(Exception e) {
        status("connection lost: " + e.getMessage());
        disconnect();
    }

    private class TextAdapter extends BaseAdapter
    {

        Context context;


        public TextAdapter(Context context)
        {
            this.context = context;
        }


        @Override
        public int getCount() {

            return itemsName.length;
        }

        @Override
        public Object getItem(int position) {
            // TODO Auto-generated method stub

            return itemsName[position];
        }

        @Override
        public long getItemId(int position) {

            // TODO Auto-generated method stub

            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            // TODO Auto-generated method stub

            TextView text = new TextView(this.context);

            text.getBaseline();
            //text.setText(itemsName[position]);

            text.setGravity(Gravity.CENTER);

            text.setBackgroundColor(getResources().getColor(R.color.sensor_def_color));

            //text.setTextColor(Color.parseColor("#040404"));

            text.setLayoutParams(new GridView.LayoutParams(40, 15));

            return text;
        }
    }
}

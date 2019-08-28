package com.example.android.camera2basic;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.UUID;


public class ledControl extends AppCompatActivity {

    Button btn1, btn2, btn3, btn4, btn5, btn6, btn7, btn8, btn9, btnDis;
    SeekBar brightness;
    TextView lumn;
    String address = null;
    private ProgressDialog progress;
    BluetoothAdapter myBluetooth = null;
    BluetoothSocket btSocket = null;
    private boolean isBtConnected = false;
    //SPP UUID. Look for it
    static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        Intent newint = getIntent();
        address = newint.getStringExtra(DeviceList.EXTRA_ADDRESS); //receive the address of the bluetooth device

        //view of the ledControl
        setContentView(R.layout.activity_led_control);

        //call the widgtes
        btn1 = (Button)findViewById(R.id.button11);
        btn2 = (Button)findViewById(R.id.button12);
        btn3 = (Button)findViewById(R.id.button13);
        btn4 = (Button)findViewById(R.id.button14);
        btn5 = (Button)findViewById(R.id.button15);
        btn6 = (Button)findViewById(R.id.button16);
        btn7 = (Button)findViewById(R.id.button17);
        btn8 = (Button)findViewById(R.id.button18);
        btn9 = (Button)findViewById(R.id.button19);
        btnDis = (Button)findViewById(R.id.button4);
        brightness = (SeekBar)findViewById(R.id.seekBar);
        //lumn = (TextView)findViewById(R.id.lumn);

        new ConnectBT().execute(); //Call the class to connect

        //commands to be sent to bluetooth
        btn1.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                Message1();      //method to turn on
            }
        });

        btn2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                Message2();   //method to turn off
            }
        });
        btn3.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                Message3();      //method to turn on
            }
        });
        btn4.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                Message4();      //method to turn on
            }
        });
        btn5.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                Message5();      //method to turn on
            }
        });
        btn6.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                Message6();      //method to turn on
            }
        });
        btn7.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                Message7();      //method to turn on
            }
        });
        btn8.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                Message8();      //method to turn on
            }
        });
        btn9.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                Message9();      //method to turn on
            }
        });


        btnDis.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                Disconnect(); //close connection
            }
        });

        brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser==true)
                {
                    lumn.setText(String.valueOf(progress));
                    try
                    {
                        btSocket.getOutputStream().write(String.valueOf(progress).getBytes());
                    }
                    catch (IOException e)
                    {

                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }

    private void Disconnect()
    {
        if (btSocket!=null) //If the btSocket is busy
        {
            try
            {
                btSocket.close(); //close connection
            }
            catch (IOException e)
            { msg("Error");}
        }
        finish(); //return to the first layout

    }



    private void Message1()
    {
        if (btSocket!=null)
        {
            try
            {
                btSocket.getOutputStream().write("1".toString().getBytes());
            }
            catch (IOException e)
            {
                msg("Error");
            }
        }
    }
    private void Message2()
    {
        if (btSocket!=null)
        {
            try
            {
                btSocket.getOutputStream().write("2".toString().getBytes());
            }
            catch (IOException e)
            {
                msg("Error");
            }
        }
    }
    private void Message3()
    {
        if (btSocket!=null)
        {
            try
            {
                btSocket.getOutputStream().write("3".toString().getBytes());
            }
            catch (IOException e)
            {
                msg("Error");
            }
        }
    }
    private void Message4()
    {
        if (btSocket!=null)
        {
            try
            {
                btSocket.getOutputStream().write("4".toString().getBytes());
            }
            catch (IOException e)
            {
                msg("Error");
            }
        }
    }
    private void Message5()
    {
        if (btSocket!=null)
        {
            try
            {
                btSocket.getOutputStream().write("5".toString().getBytes());
            }
            catch (IOException e)
            {
                msg("Error");
            }
        }
    }
    private void Message6()
    {
        if (btSocket!=null)
        {
            try
            {
                btSocket.getOutputStream().write("6".toString().getBytes());
            }
            catch (IOException e)
            {
                msg("Error");
            }
        }
    }
    private void Message7()
    {
        if (btSocket!=null)
        {
            try
            {
                btSocket.getOutputStream().write("7".toString().getBytes());
            }
            catch (IOException e)
            {
                msg("Error");
            }
        }
    }
    private void Message8()
    {
        if (btSocket!=null)
        {
            try
            {
                btSocket.getOutputStream().write("8".toString().getBytes());
            }
            catch (IOException e)
            {
                msg("Error");
            }
        }
    }
    private void Message9()
    {
        if (btSocket!=null)
        {
            try
            {
                btSocket.getOutputStream().write("9".toString().getBytes());
            }
            catch (IOException e)
            {
                msg("Error");
            }
        }
    }

    // fast way to call Toast
    private void msg(String s)
    {
        Toast.makeText(getApplicationContext(),s, Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_led_control, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private class ConnectBT extends AsyncTask<Void, Void, Void>  // UI thread
    {
        private boolean ConnectSuccess = true; //if it's here, it's almost connected

        @Override
        protected void onPreExecute()
        {
            progress = ProgressDialog.show(ledControl.this, "Connecting...", "Please wait!!!");  //show a progress dialog
        }

        @Override
        protected Void doInBackground(Void... devices) //while the progress dialog is shown, the connection is done in background
        {
            try
            {
                if (btSocket == null || !isBtConnected)
                {
                 myBluetooth = BluetoothAdapter.getDefaultAdapter();//get the mobile bluetooth device
                 BluetoothDevice dispositivo = myBluetooth.getRemoteDevice(address);//connects to the device's address and checks if it's available
                 btSocket = dispositivo.createInsecureRfcommSocketToServiceRecord(myUUID);//create a RFCOMM (SPP) connection
                 BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                 btSocket.connect();//start connection
                }
            }
            catch (IOException e)
            {
                ConnectSuccess = false;//if the try failed, you can check the exception here
            }
            return null;
        }
        @Override
        protected void onPostExecute(Void result) //after the doInBackground, it checks if everything went fine
        {
            super.onPostExecute(result);

            if (!ConnectSuccess)
            {
                msg("Connection Failed. Is it a SPP Bluetooth? Try again.");
                finish();
            }
            else
            {
                msg("Connected.");
                isBtConnected = true;
            }
            progress.dismiss();
        }
    }
}

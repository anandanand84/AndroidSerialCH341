package com.wch.wchusbdriver;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.hardware.usb.UsbManager;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

public class UartLoopBackActivity extends Activity {
	public static final String TAG = "com.wch.wchusbdriver";
	private static final String ACTION_USB_PERMISSION = "com.wch.wchusbdriver.USB_PERMISSION";

	private static SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ");

	private static SimpleDateFormat fileFormatter = new SimpleDateFormat("yyyy-MM-dd");


	/* thread to read the data */
	public readThread handlerThread;
	protected final Object ThreadLock = new Object();
	public String filename = "/sdcard/irsignal/recieved"+fileFormatter.format(Calendar.getInstance().getTime()) +".txt";
	public String string = "Hello world!";
	BufferedOutputStream outputStream;


	/* declare UART interface variable */
	public CH34xAndroidDriver uartInterface;
	
	EditText readText;
	EditText writeText;
	Spinner baudSpinner;
	Spinner stopSpinner;
	Spinner dataSpinner;
	Spinner paritySpinner;
	Spinner flowSpinner;

	Button writeButton, configButton;
	
	byte[] writeBuffer;
	char[] readBuffer;
	int actualNumBytes;

	int numBytes;
	byte count;
	int status;
	byte writeIndex = 0;
	byte readIndex = 0;

	int baudRate; /* baud rate */
	byte baudRate_byte; /* baud rate */ //send to hardware by AOA
	byte stopBit; /* 1:1stop bits, 2:2 stop bits */
	byte dataBit; /* 8:8bit, 7: 7bit 6: 6bit 5: 5bit*/
	byte parity; /* 0: none, 1: odd, 2: even, 3: mark, 4: space */
	byte flowControl; /* 0:none, 1: flow control(CTS,RTS) */
	//byte timeout; // time out 
	public Context global_context;
	public boolean isConfiged = false;
	public boolean READ_ENABLE = false;
	public SharedPreferences sharePrefSettings;
	Drawable originalDrawable;
	public String act_string; 
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		/* create editable text objects */
		readText = (EditText) findViewById(R.id.ReadValues);
		writeText = (EditText) findViewById(R.id.WriteValues);

		global_context = this;

		configButton = (Button) findViewById(R.id.configButton);
		writeButton = (Button) findViewById(R.id.WriteButton);
		
		originalDrawable = configButton.getBackground();
		
		/* allocate buffer */
		writeBuffer = new byte[512];
		readBuffer = new char[512];
		
		/* setup the baud rate list */
		baudSpinner = (Spinner)findViewById(R.id.baudRateValue);
		ArrayAdapter<CharSequence> baudAdapter = ArrayAdapter.createFromResource(this, R.array.baud_rate,
				R.layout.my_spinner_textview);
		baudAdapter.setDropDownViewResource(R.layout.my_spinner_textview);		
		baudSpinner.setAdapter(baudAdapter);
		baudSpinner.setGravity(0x10);
		baudSpinner.setSelection(5);
		/* by default it is 9600 */
		baudRate = 9600;
		
		/* stop bits */
		stopSpinner = (Spinner) findViewById(R.id.stopBitValue);
		ArrayAdapter<CharSequence> stopAdapter = ArrayAdapter.createFromResource(this, R.array.stop_bits,
						R.layout.my_spinner_textview);
		stopAdapter.setDropDownViewResource(R.layout.my_spinner_textview);
		stopSpinner.setAdapter(stopAdapter);
		stopSpinner.setGravity(0x01);
		/* default is stop bit 1 */
		stopBit = 1;
		
		/* data bits */
		dataSpinner = (Spinner) findViewById(R.id.dataBitValue);
		ArrayAdapter<CharSequence> dataAdapter = ArrayAdapter.createFromResource(this, R.array.data_bits,
						R.layout.my_spinner_textview);
		dataAdapter.setDropDownViewResource(R.layout.my_spinner_textview);
		dataSpinner.setAdapter(dataAdapter);
		dataSpinner.setGravity(0x11);
		dataSpinner.setSelection(3);
		/* default data bit is 8 bit */
		dataBit = 8;
		
		/* parity */
		paritySpinner = (Spinner) findViewById(R.id.parityValue);
		ArrayAdapter<CharSequence> parityAdapter = ArrayAdapter.createFromResource(this, R.array.parity,
						R.layout.my_spinner_textview);
		parityAdapter.setDropDownViewResource(R.layout.my_spinner_textview);
		paritySpinner.setAdapter(parityAdapter);
		paritySpinner.setGravity(0x11);
		/* default is none */
		parity = 0;

		/* flow control */
		flowSpinner = (Spinner) findViewById(R.id.flowControlValue);
		ArrayAdapter<CharSequence> flowAdapter = ArrayAdapter.createFromResource(this, R.array.flow_control,
						R.layout.my_spinner_textview);
		flowAdapter.setDropDownViewResource(R.layout.my_spinner_textview);
		flowSpinner.setAdapter(flowAdapter);
		flowSpinner.setGravity(0x11);
		/* default flow control is is none */
		flowControl = 0;
		
		/* set the adapter listeners for baud */
		baudSpinner.setOnItemSelectedListener(new MyOnBaudSelectedListener());
		/* set the adapter listeners for stop bits */
		stopSpinner.setOnItemSelectedListener(new MyOnStopSelectedListener());
		/* set the adapter listeners for data bits */
		dataSpinner.setOnItemSelectedListener(new MyOnDataSelectedListener());
		/* set the adapter listeners for parity */
		paritySpinner.setOnItemSelectedListener(new MyOnParitySelectedListener());
		/* set the adapter listeners for flow control */
		flowSpinner.setOnItemSelectedListener(new MyOnFlowSelectedListener());		
		
		configButton.setOnClickListener(new OpenDeviceListener());
		writeButton.setOnClickListener(new OnClickedWriteButton());
		
		writeButton.setEnabled(false);
		
		
		uartInterface = new CH34xAndroidDriver(
				(UsbManager) getSystemService(Context.USB_SERVICE), this,
				ACTION_USB_PERMISSION);
		
		act_string = getIntent().getAction();
		if(-1 != act_string.indexOf("android.intent.action.MAIN"))
		{
			Log.d(TAG, "android.intent.action.MAIN");
		} else if(-1 != act_string.indexOf("android.hardware.usb.action.USB_DEVICE_ATTACHED"))
		{
			Log.d(TAG, "android.hardware.usb.action.USB_DEVICE_ATTACHED");
		}
		
		if(!uartInterface.UsbFeatureSupported())
		{
			Toast.makeText(this, "No Support USB host API", Toast.LENGTH_SHORT)
			.show();
			readText.setText("No Support USB host API");
			uartInterface = null;
		}

		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
		
		if(READ_ENABLE == false) {
			READ_ENABLE = true;
			handlerThread = new readThread(handler);
			handlerThread.start();
		}
	}

	public class OpenDeviceListener implements View.OnClickListener
	{

		@Override
		public void onClick(View v) {
			// TODO Auto-generated method stub
			boolean flags;
			if(false == isConfiged) {
				isConfiged = true;
				writeButton.setEnabled(true);
				if(uartInterface.isConnected()) {
					try {
						outputStream = new BufferedOutputStream(new FileOutputStream(new File(filename)),512 * 1024);
					} catch (Exception e) {
						Toast.makeText(global_context, "Error Occured writing to file "+e.getMessage(), Toast.LENGTH_SHORT).show();
					}
					flags = uartInterface.UartInit();
					if(!flags) {
						Log.d(TAG, "Init Uart Error");
						Toast.makeText(global_context, "Init Uart Error", Toast.LENGTH_SHORT).show();
					} else {
						if(uartInterface.SetConfig(baudRate, dataBit, stopBit, parity, flowControl)) {
							Log.d(TAG, "Configed");
						}
					}
				}
				
				if(isConfiged == true) {
					configButton.setEnabled(false);
				}
			}
			
			
		}
		
	}


	
	public class OnClickedWriteButton implements View.OnClickListener
	{

		@Override
		public void onClick(View v) {
			// TODO Auto-generated method stub
			int count_int;
			int NumBytes = 0;
			int mLen = 0;
			
			if(writeText.length() != 0) {
				NumBytes = writeText.length();
				for(count_int = 0; count_int < NumBytes; count_int++) {
					writeBuffer[count_int] = (byte)writeText.getText().charAt(count_int);
				}
			}
			try {
				mLen = uartInterface.WriteData(writeBuffer, NumBytes);
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				Toast.makeText(global_context, "WriteData Error", Toast.LENGTH_SHORT).show();
				e1.printStackTrace();
			}
			
			if(NumBytes != mLen) {
				Toast.makeText(global_context, "WriteData Error", Toast.LENGTH_SHORT).show();
			}
			Log.d(TAG, "WriteData Length is " + mLen);
		}
		
	}
	
	public class MyOnBaudSelectedListener implements OnItemSelectedListener {

		@Override
		public void onItemSelected(AdapterView<?> parent, View view,
				int position, long id) {
			// TODO Auto-generated method stub
			baudRate = Integer.parseInt(parent.getItemAtPosition(position).toString());
		}

		@Override
		public void onNothingSelected(AdapterView<?> parent) {
			// TODO Auto-generated method stub
		}
	}
	
	public class MyOnStopSelectedListener implements OnItemSelectedListener {

		@Override
		public void onItemSelected(AdapterView<?> parent, View view,
				int position, long id) {
			// TODO Auto-generated method stub
			stopBit = (byte)Integer.parseInt(parent.getItemAtPosition(position).toString());
		}

		@Override
		public void onNothingSelected(AdapterView<?> parent) {
			// TODO Auto-generated method stub
			
		}
		
	}
	
	public class MyOnDataSelectedListener implements OnItemSelectedListener {

		@Override
		public void onItemSelected(AdapterView<?> parent, View view,
				int position, long id) {
			// TODO Auto-generated method stub
			dataBit = (byte)Integer.parseInt(parent.getItemAtPosition(position).toString());
		}

		@Override
		public void onNothingSelected(AdapterView<?> parent) {
			// TODO Auto-generated method stub
			
		}
		
	}
	
	public class MyOnParitySelectedListener implements OnItemSelectedListener {

		@Override
		public void onItemSelected(AdapterView<?> parent, View view,
				int position, long id) {
			// TODO Auto-generated method stub
			String parityString = new String(parent.getItemAtPosition(position).toString());
			if(parityString.compareTo("None") == 0) {
				parity = 0;
			}
			
			if(parityString.compareTo("Odd") == 0) {
				parity = 1;
			}
			
			if(parityString.compareTo("Even") == 0) {
				parity = 2;
			}
			
			if(parityString.compareTo("Mark") == 0) {
				parity = 3;
			}
			
			if(parityString.compareTo("Space") == 0) {
				parity = 4;
			}
		}

		@Override
		public void onNothingSelected(AdapterView<?> parent) {
			// TODO Auto-generated method stub
			
		}
		
	}
	
	public class MyOnFlowSelectedListener implements OnItemSelectedListener {

		@Override
		public void onItemSelected(AdapterView<?> parent, View view,
				int position, long id) {
			// TODO Auto-generated method stub
			String flowString = new String(parent.getItemAtPosition(position).toString());
			if(flowString.compareTo("None") == 0) {
				flowControl = 0;
			}
			
			if(flowString.compareTo("CTS/RTS") == 0) {
				flowControl = 1;
			}
		}

		@Override
		public void onNothingSelected(AdapterView<?> parent) {
			// TODO Auto-generated method stub
			
		}
		
	}
	
	public void onHomePressed() {
		onBackPressed();
	}
	
	public void onBackPressed() {
		super.onBackPressed();
	}
	
	protected void onResume() {
		super.onResume();
		if(2 == uartInterface.ResumeUsbList())
		{
			uartInterface.CloseDevice();
			Log.d(TAG, "Enter onResume Error");
		}
	}
	
	protected void onPause() {
		super.onPause();
	}
	
	protected void onStop() {
		if(READ_ENABLE == true) {
			READ_ENABLE = false;
		}
		super.onStop();
	}
	
	protected void onDestroy() {
		if(uartInterface != null) {
			if(uartInterface.isConnected()) {
				uartInterface.CloseDevice();
				try {
					outputStream.close();
				} catch (Exception e) {
					Toast.makeText(global_context, "Error Occured writing to file "+e.getMessage(), Toast.LENGTH_SHORT).show();
				}
			}
			uartInterface = null;
		}

		super.onDestroy();
	}
	
	final Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			
			if (actualNumBytes != 0x00) {
				String stringData = String.copyValueOf(readBuffer, 0,
						actualNumBytes);
				readText.append(stringData);

				try {
					stringData = formatter.format(Calendar.getInstance().getTime()) + stringData;
					outputStream.write(stringData.getBytes());
				} catch (Exception e) {
					Toast.makeText(global_context, "Error Occured writing to file "+e.getMessage(), Toast.LENGTH_SHORT).show();
				}
				actualNumBytes = 0;

			}
			
		}
	};

	/* usb input data handler */
	private class readThread extends Thread {
		Handler mHandler;

		/* constructor */
		Handler mhandler;
		readThread(Handler h) {
			mhandler = h;
			this.setPriority(Thread.MIN_PRIORITY);
		}
		
		public void run() {
			while(READ_ENABLE) {
				Message msg = mhandler.obtainMessage();
				try {
					Thread.sleep(50);
				} catch(InterruptedException e) {
				}
//				Log.d(TAG, "Thread");
				synchronized (ThreadLock) {
					if(uartInterface != null) {
						actualNumBytes = uartInterface.ReadData(readBuffer, 64);

						if(actualNumBytes > 0)
						{
							mhandler.sendMessage(msg);
						}
					}
				}	
			}
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.uart_loop_back, menu);
		return true;
	}

}

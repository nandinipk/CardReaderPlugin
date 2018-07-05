package com.egood.cordova.plugins.magtekudynamo;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.util.*;

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.apache.cordova.CallbackContext;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import com.magtek.mobile.android.mtlib.MTEMVDeviceConstants;
import com.magtek.mobile.android.mtlib.MTSCRA;
import com.magtek.mobile.android.mtlib.MTConnectionType;
import com.magtek.mobile.android.mtlib.MTSCRAEvent;
import com.magtek.mobile.android.mtlib.MTEMVEvent;
import com.magtek.mobile.android.mtlib.MTConnectionState;
import com.magtek.mobile.android.mtlib.MTCardDataState;
import com.magtek.mobile.android.mtlib.MTDeviceConstants;
import com.magtek.mobile.android.mtlib.IMTCardData;
import com.magtek.mobile.android.mtlib.config.MTSCRAConfig;
import com.magtek.mobile.android.mtlib.config.ProcessMessageResponse;
import com.magtek.mobile.android.mtlib.config.SCRAConfigurationDeviceInfo;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Message;
import android.os.Handler.Callback;

import DukptDecrypt;
public class MagTekUDynamoPlugin extends CordovaPlugin {
    private final static String TAG = MagTekUDynamoPlugin.class.getSimpleName();
    final HeadSetBroadCastReceiver m_headsetReceiver = new HeadSetBroadCastReceiver();
    final NoisyAudioStreamReceiver m_noisyAudioStreamReceiver = new NoisyAudioStreamReceiver();
    
    private Handler m_srcaHandler = new Handler(new SCRAHandlerCallback());

    private AudioManager m_audioManager;
    private int m_audioVolume;
	private MTSCRA m_scra;


	private boolean m_audioConnected;
	private CallbackContext m_eventListenerCallback;
    private boolean m_hasRequiredPermissions = false;

	public static final int RECORD_AUDIO_REQ_CODE = 0;
    public static final int MODIFY_AUDIO_REQ_CODE = 1;

	private void Initialize() {
		if(m_scra == null) {
			m_scra = new MTSCRA(this.cordova.getActivity(), m_srcaHandler);
		}
		if(m_audioManager == null) {
            m_audioManager = (AudioManager) cordova.getActivity().getSystemService(Context.AUDIO_SERVICE);
		}

        m_scra.clearBuffers();
        m_audioConnected = false;

		onResume(false);
	}

	@Override
	public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException
	{
		for(int r:grantResults)
		{
			if(r == PackageManager.PERMISSION_DENIED)
			{
                switch(requestCode)
                {
                    case RECORD_AUDIO_REQ_CODE:
                    case MODIFY_AUDIO_REQ_CODE:
                        m_hasRequiredPermissions = false;
                        break;
                }

			    if (m_eventListenerCallback != null) {
                    requiredPermissionsError(m_eventListenerCallback);
                }
				return;
			}
		}


	}

	public void requiredPermissionsError(CallbackContext context) {
        if (context != null) {
            context.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "Required permissions missing, magtek uDynamo device disabled"));
        }
        return;
    }


    private String getManualAudioConfig()
    {
        String config = "";

        try
        {
            String model = android.os.Build.MODEL.toUpperCase();

            if(model.contains("DROID RAZR") || model.toUpperCase().contains("XT910"))
            {
                config = "INPUT_SAMPLE_RATE_IN_HZ=48000,";
            }
            else if ((model.equals("DROID PRO"))||
                    (model.equals("MB508"))||
                    (model.equals("DROIDX"))||
                    (model.equals("DROID2"))||
                    (model.equals("MB525")))
            {
                config = "INPUT_SAMPLE_RATE_IN_HZ=32000,";
            }
            else if ((model.equals("GT-I9300"))||//S3 GSM Unlocked
                    (model.equals("SPH-L710"))||//S3 Sprint
                    (model.equals("SGH-T999"))||//S3 T-Mobile
                    (model.equals("SCH-I535"))||//S3 Verizon
                    (model.equals("SCH-R530"))||//S3 US Cellular
                    (model.equals("SAMSUNG-SGH-I747"))||// S3 AT&T
                    (model.equals("M532"))||//Fujitsu
                    (model.equals("GT-N7100"))||//Notes 2
                    (model.equals("GT-N7105"))||//Notes 2
                    (model.equals("SAMSUNG-SGH-I317"))||// Notes 2
                    (model.equals("SCH-I605"))||// Notes 2
                    (model.equals("SCH-R950"))||// Notes 2
                    (model.equals("SGH-T889"))||// Notes 2
                    (model.equals("SPH-L900"))||// Notes 2
                    (model.equals("GT-P3113")))//Galaxy Tab 2, 7.0

            {
                config = "INPUT_AUDIO_SOURCE=VRECOG,";
            }
            else if ((model.equals("XT907")))
            {
                config = "INPUT_WAVE_FORM=0,";
            }
            else
            {
                config = "INPUT_AUDIO_SOURCE=VRECOG,";
                //config += "PAN_MOD10_CHECKDIGIT=FALSE";
            }

        }
        catch (Exception ex)
        {

        }

        return config;
    }

    public boolean requestRequiredPermissions() {
        boolean result = true;
	    if (!cordova.hasPermission(Manifest.permission.MODIFY_AUDIO_SETTINGS)) {
            cordova.requestPermission(this, MODIFY_AUDIO_REQ_CODE, Manifest.permission.MODIFY_AUDIO_SETTINGS);
            result = false;
        }

        if (!cordova.hasPermission(Manifest.permission.RECORD_AUDIO)) {
            cordova.requestPermission(this, RECORD_AUDIO_REQ_CODE,  Manifest.permission.RECORD_AUDIO);
            result = false;
        }

        return result;
    }


	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        PluginResult pr = new PluginResult(PluginResult.Status.ERROR, "Unhandled execute call: " + action);

        //trigger required permissions request
        m_hasRequiredPermissions = requestRequiredPermissions();

        if(m_scra == null) {
            Initialize();
        }

        if(action.equals("openDevice")) {
            //enforce permissions before allowing device open
            if (!m_hasRequiredPermissions) {
                requestRequiredPermissions();
                requiredPermissionsError(callbackContext);
                return true;
            }

			if(m_audioConnected) {
                m_scra.setConnectionType(MTConnectionType.Audio);
                m_scra.setAddress(null);
                m_scra.setDeviceConfiguration(getManualAudioConfig());
                m_scra.openDevice();

                pr = new PluginResult(PluginResult.Status.OK, m_scra.isDeviceConnected());
            } else {
                pr = new PluginResult(PluginResult.Status.ERROR, "No reader attached/detected.");
            }
		}
		else if(action.equals("closeDevice")) {
			m_scra.closeDevice();

			pr = new PluginResult(PluginResult.Status.OK, !m_scra.isDeviceConnected());
		}
		else if(action.equals("isDeviceConnected")) {
			pr = new PluginResult(PluginResult.Status.OK, m_scra.isDeviceConnected());
		}
		else if(action.equals("isDeviceOpened")) {
			pr = new PluginResult(PluginResult.Status.OK, m_scra.isDeviceConnected());
		}
		else if(action.equals("clearCardData")) {
			pr = new PluginResult(PluginResult.Status.OK);
		}
		else if(action.equals("getTrack1")) {
			pr = new PluginResult(PluginResult.Status.OK, m_scra.getTrack1());
		}
		else if(action.equals("getTrack2")) {
			pr = new PluginResult(PluginResult.Status.OK, m_scra.getTrack2());
		}
		else if(action.equals("getTrack3")) {
			pr = new PluginResult(PluginResult.Status.OK, m_scra.getTrack3());
		}
		else if(action.equals("getTrack1Masked")) {
			pr = new PluginResult(PluginResult.Status.OK, m_scra.getTrack1Masked());
		}
		else if(action.equals("getTrack2Masked")) {
			pr = new PluginResult(PluginResult.Status.OK, m_scra.getTrack2Masked());
		}
		else if(action.equals("getTrack3Masked")) {
			pr = new PluginResult(PluginResult.Status.OK, m_scra.getTrack3Masked());
		}
		else if(action.equals("getMagnePrintStatus")) {
			pr = new PluginResult(PluginResult.Status.OK, m_scra.getMagnePrintStatus());
		}
		else if(action.equals("getMagnePrint")) {
			pr = new PluginResult(PluginResult.Status.OK, m_scra.getMagnePrint());
		}
		else if(action.equals("getDeviceSerial")) {
			pr = new PluginResult(PluginResult.Status.OK, m_scra.getDeviceSerial());
		}
		else if(action.equals("getSessionID")) {
			pr = new PluginResult(PluginResult.Status.OK, m_scra.getSessionID());
		}
		else if(action.equals("listenForEvents")) {
			pr = new PluginResult(PluginResult.Status.NO_RESULT);
			pr.setKeepCallback(true);

			m_eventListenerCallback = callbackContext;
		}
		else if(action.equals("getCardName")) {
			pr = new PluginResult(PluginResult.Status.OK, m_scra.getCardName());
		}
		else if(action.equals("getCardIIN")) {
			pr = new PluginResult(PluginResult.Status.OK, m_scra.getCardIIN());
		}
		else if(action.equals("getCardLast4")) {
			pr = new PluginResult(PluginResult.Status.OK, m_scra.getCardLast4());
		}
		else if(action.equals("getCardExpDate")) {
			pr = new PluginResult(PluginResult.Status.OK, m_scra.getCardExpDate());
		}
		else if(action.equals("getCardServiceCode")) {
			pr = new PluginResult(PluginResult.Status.OK, m_scra.getCardServiceCode());
			pr = new PluginResult(PluginResult.Status.OK, m_scra.getCardIIN());
		}
		else if(action.equals("getCardLast4")) {
			pr = new PluginResult(PluginResult.Status.OK, m_scra.getCardLast4());
		}
		else if(action.equals("getCardExpDate")) {
			pr = new PluginResult(PluginResult.Status.OK, m_scra.getCardExpDate());
		}
		else if(action.equals("getCardServiceCode")) {
			pr = new PluginResult(PluginResult.Status.OK, m_scra.getCardServiceCode());
		}
		else if(action.equals("getCardStatus")) {
			pr = new PluginResult(PluginResult.Status.OK, m_scra.getCardStatus());
		}
		else if(action.equals("setDeviceType")) {
			;
		}

		callbackContext.sendPluginResult(pr);

		return true;
	}

    @Override
    public void onResume(boolean multitasking) {
    	super.onResume(multitasking);
    	
        cordova.getActivity().getApplicationContext().registerReceiver(m_headsetReceiver, new IntentFilter(Intent.ACTION_HEADSET_PLUG));
        cordova.getActivity().getApplicationContext().registerReceiver(m_noisyAudioStreamReceiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity
        // returns.
    }

    @Override
    public void onDestroy() {
        cordova.getActivity().getApplicationContext().unregisterReceiver(m_headsetReceiver);
        cordova.getActivity().getApplicationContext().unregisterReceiver(m_noisyAudioStreamReceiver);
        if (m_scra != null) {
            m_scra.closeDevice();
        }

    	super.onDestroy();
    }

    private void setVolume(int volume)
    {
        m_audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, AudioManager.FLAG_SHOW_UI);
    }

    private void saveVolume()
    {
        m_audioVolume = m_audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
    }

    private void restoreVolume()
    {
        setVolume(m_audioVolume);
    }

    private void setVolumeToMax()
    {
        saveVolume();

        int volume = m_audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

        setVolume(volume);
    }

	private void sendCardData() throws JSONException {
		JSONObject response = new JSONObject();

        DukptDecrypt d = DukptDecrypt();
        String track2Data = d.decrypt(m_scra.getKSN(), '0123456789ABCDEFFEDCBA9876543210', m_scra.getTrack2());
        response.put("Card.DecryptedTrack2", track2Data);
		response.put("Response.Type", m_scra.getResponseType());
		response.put("Track.Status", m_scra.getTrackDecodeStatus());
		response.put("Card.Status", m_scra.getCardStatus());
		response.put("Encryption.Status", m_scra.getEncryptionStatus());
		response.put("Battery.Level", m_scra.getBatteryLevel());
//		response.put("Swipe.Count", m_scra.getSwipeCount());
		response.put("Track.Masked", m_scra.getMaskedTracks());
		response.put("MagnePrint.Status", m_scra.getMagnePrintStatus());
		response.put("SessionID", m_scra.getSessionID());
		response.put("Card.SvcCode", m_scra.getCardServiceCode());
		response.put("Card.PANLength", m_scra.getCardPANLength());
		response.put("KSN", m_scra.getKSN());
		response.put("Device.SerialNumber", m_scra.getDeviceSerial());
		response.put("TLV.CARDIIN", m_scra.getTagValue("TLV_CARDIIN", ""));
		response.put("MagTekSN", m_scra.getMagTekDeviceSerial());
		response.put("FirmPartNumber", m_scra.getFirmware());
		response.put("TLV.Version", m_scra.getTLVVersion());
		response.put("DevModelName", m_scra.getDeviceName());
		response.put("MSR.Capability", m_scra.getCapMSR());
		response.put("Tracks.Capability", m_scra.getCapTracks());
		response.put("Encryption.Capability", m_scra.getCapMagStripeEncryption());
		response.put("Card.IIN", m_scra.getCardIIN());
		response.put("Card.Name", m_scra.getCardName());
		response.put("Card.Last4", m_scra.getCardLast4());
		response.put("Card.ExpDate", m_scra.getCardExpDate());
		response.put("Track1.Masked", m_scra.getTrack1Masked());
		response.put("Track2.Masked", m_scra.getTrack2Masked());
		response.put("Track3.Masked", m_scra.getTrack3Masked());
		response.put("Track1", m_scra.getTrack1());
		response.put("Track2", m_scra.getTrack2());
		response.put("Track3", m_scra.getTrack3());
		response.put("MagnePrint", m_scra.getMagnePrint());
		response.put("RawResponse", m_scra.getResponseData());

		m_eventListenerCallback.success(response);
	}

	private void sendCardError() {
		m_eventListenerCallback.error("That card was not swiped properly. Please try again.");
	}

    private class SCRAHandlerCallback implements Callback  {
        public boolean handleMessage(Message msg)
        {
            try
            {
                switch (msg.what)
                {
                    case MTSCRAEvent.OnDeviceConnectionStateChanged:
                        OnDeviceStateChanged((MTConnectionState) msg.obj);
                        break;
                    case MTSCRAEvent.OnCardDataStateChanged:
                        OnCardDataStateChanged((MTCardDataState) msg.obj);
                        break;
                    case MTSCRAEvent.OnDataReceived:
                        OnCardDataReceived((IMTCardData) msg.obj);
                        break;
                    case MTSCRAEvent.OnDeviceResponse:
                        OnDeviceResponse((String) msg.obj);
                        break;
                }
            }
            catch (Exception ex)
            {

            }

            return true;
        }
    }

    protected void OnDeviceStateChanged(MTConnectionState deviceState)
    {
        //MTConnectionState tPrevState =m_connectionState;

        //setState(deviceState);
        //updateDisplay();
        //invalidateOptionsMenu();

        switch (deviceState)
        {
            case Disconnected:
                Log.i(TAG, "OnDeviceStateChanged=Disconnected");
                restoreVolume();
                break;
            case Connected:
                Log.i(TAG, "OnDeviceStateChanged=Connected");
                setVolumeToMax();
                break;
            case Error:
                Log.i(TAG, "OnDeviceStateChanged=Error");
                //sendToDisplay("[Device State Error]");
                break;
            case Connecting:
                Log.i(TAG, "OnDeviceStateChanged=Connecting");
                break;
            case Disconnecting:
                Log.i(TAG, "OnDeviceStateChanged=Disconnecting");
                break;
        }
    }

    protected void OnCardDataStateChanged(MTCardDataState cardDataState)
    {
        switch (cardDataState)
        {
            case DataNotReady:
                Log.i(TAG, "OnCardDataStateChanged=DataNotReady");
                break;
            case DataReady:
                Log.i(TAG, "OnCardDataStateChanged=DataReady");
                break;
            case DataError:
                Log.i(TAG, "OnCardDataStateChanged=DataError");
                sendCardError();
                break;
        }
    }

    protected void OnCardDataReceived(IMTCardData cardData)
    {
        Log.i(TAG, "OnCardDataReceived");
        try {

            sendCardData();
        } catch (Exception ex) {
            Log.e(TAG, "OnCardDataReceived", ex);
        }
    }

    protected void OnDeviceResponse(String data)
    {
        Log.i(TAG, "OnDeviceResponse=" + data );
    }

	public class NoisyAudioStreamReceiver extends BroadcastReceiver
    {
    	@Override
    	public void onReceive(Context context, Intent intent)
    	{
    		/* If the device is unplugged, this will immediately detect that action,
    		 * and close the device.
    		 */
    		if(AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction()))
    		{
            	m_audioConnected=false;
                if(m_scra.isDeviceConnected())
                {
                    m_scra.closeDevice();
                }
    		}
    	}
    }

	public class HeadSetBroadCastReceiver extends BroadcastReceiver
    {

        @Override
        public void onReceive(Context context, Intent intent) {
        	try
        	{
                String action = intent.getAction();
                if( (action.compareTo(Intent.ACTION_HEADSET_PLUG)) == 0)   //if the action match a headset one
                {
                    int headSetState = intent.getIntExtra("state", 0);      //get the headset state property
                    int hasMicrophone = intent.getIntExtra("microphone", 0);//get the headset microphone property

                    if( (headSetState == 1) && (hasMicrophone == 1))        //headset was unplugged & has no microphone
                    {
                    	m_audioConnected = true;
                    }
                    else
                    {
                    	m_audioConnected = false;
                        if(m_scra.isDeviceConnected())
                        {
                            m_scra.closeDevice();
                        }
                    }

                }

        	}
        	catch(Exception ex)
        	{
                Log.e(TAG, "HeadSetBroadCastReceiver onReceive Error", ex);
            }
        }
    }
}
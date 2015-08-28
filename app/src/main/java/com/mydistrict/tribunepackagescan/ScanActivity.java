package com.mydistrict.tribunepackagescan;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.symbol.emdk.*;
import com.symbol.emdk.EMDKManager.EMDKListener;
import com.symbol.emdk.barcode.BarcodeManager;
import com.symbol.emdk.barcode.ScanDataCollection;
import com.symbol.emdk.barcode.Scanner;
import com.symbol.emdk.barcode.Scanner.DataListener;
import com.symbol.emdk.barcode.Scanner.StatusListener;
import com.symbol.emdk.barcode.ScannerException;
import com.symbol.emdk.barcode.ScannerResults;
import com.symbol.emdk.barcode.StatusData;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;


public class ScanActivity extends Activity implements EMDKListener, StatusListener, DataListener{

    private String profileName="DataCaptureProfile";
    private ProfileManager mProfileManager = null;
    private EMDKManager emdkManager = null;
    private Scanner scanner = null;
    private TextView statusTextView = null;
    private TextView resultTextView = null;
    private EditText dataView = null;
    private BarcodeManager barcodeManager=null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        if (android.os.Build.VERSION.SDK_INT > 9) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }
        statusTextView = (TextView) findViewById(R.id.textViewStatus);
        resultTextView = (TextView) findViewById(R.id.textViewResult);
        dataView = (EditText) findViewById(R.id.editText1);


        EMDKResults results = EMDKManager.getEMDKManager(getApplicationContext(), this);
        if(results.statusCode== EMDKResults.STATUS_CODE.FAILURE){
            statusTextView.setText("EMDK Manager Request Failed");
            /*Log.v("test", "error");*/
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_scan, menu);
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

    @Override
    public void onData(ScanDataCollection scanDataCollection) {
        new AsyncDataUpdate().execute(scanDataCollection);

    }
    // Update the scan data on UI
    int dataLength = 0;

    // AsyncTask that configures the scanned data on background
    // thread and updated the result on UI thread with scanned data and type of
    // label
    private class AsyncDataUpdate extends
            AsyncTask<ScanDataCollection, Void, String> {

        @Override
        protected String doInBackground(ScanDataCollection... params) {

            // Status string that contains both barcode data and type of barcode
            // that is being scanned
            String statusStr = "";

            try {

                // Starts an asynchronous Scan. The method will not turn ON the
                // scanner. It will, however, put the scanner in a state in
                // which
                // the scanner can be turned ON either by pressing a hardware
                // trigger or can be turned ON automatically.
                scanner.read();

                ScanDataCollection scanDataCollection = params[0];

                // The ScanDataCollection object gives scanning result and the
                // collection of ScanData. So check the data and its status
                if (scanDataCollection != null
                        && scanDataCollection.getResult() == ScannerResults.SUCCESS) {

                    ArrayList<ScanDataCollection.ScanData> scanData = scanDataCollection
                            .getScanData();

                    // Iterate through scanned data and prepare the statusStr
                    for (ScanDataCollection.ScanData data : scanData) {
                        // Get the scanned data
                        String barcodeData = data.getData();
                        // Get the type of label being scanned
                        ScanDataCollection.LabelType labelType = data.getLabelType();
                        // Concatenate barcode data and label type
                        statusStr = barcodeData; /* + " " + labelType;*/
                    }
                }

            } catch (ScannerException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            // Return result to populate on UI thread
            return statusStr;
        }

        @Override
        protected void onPostExecute(String result) {
            // Update the dataView EditText on UI thread with barcode data and
            // its label type
            //if (dataLength++ > 50) {
            // Clear the cache after 50 scans
            dataView.getText().clear();
            dataLength = 0;
            //}
            statusTextView.setText("Fetching data...");
            String url = "http://www.mydistrict.net/services/api/custom/tribune/packagescan/53/1/" + result;

            new RetrievePackageInfo(url,"GET").execute();

        }

        @Override
        protected void onPreExecute() {
        }

        @Override
        protected void onProgressUpdate(Void... values) {
        }
    }
    @Override
    public void onOpened(EMDKManager emdkManager) {
        this.emdkManager=emdkManager;
        mProfileManager=(ProfileManager) emdkManager.getInstance(EMDKManager.FEATURE_TYPE.PROFILE);
        if(mProfileManager!= null){
            try{
                initializeScanner();
                String[] modifyData = new String[1];
                /*EMDKResults results = mProfileManager.processProfile(profileName, ProfileManager.PROFILE_FLAG.SET, modifyData);
                if(results.statusCode == EMDKResults.STATUS_CODE.FAILURE)
                {
                   Log.v("test", "error");
                }*/
            }
            catch(Exception ex){
                ex.printStackTrace();
                /*Log.v("test", "error");*/
            }
            Toast.makeText(this, "Press Scan Button to start scanning...", Toast.LENGTH_SHORT).show();
        }

    }
    @Override
    protected void onStop(){
        super.onStop();
        try{
            if(scanner!=null){
                scanner.disable();
                scanner=null;
            }
        }catch(ScannerException e){
            e.printStackTrace();
        }
    }

    @Override
    public void onStatus(StatusData statusData) {
        new AsyncStatusUpdate().execute(statusData);
    }

    // AsyncTask that configures the current state of scanner on background
    // thread and updates the result on UI thread
    private class AsyncStatusUpdate extends AsyncTask<StatusData, Void, String> {

        @Override
        protected String doInBackground(StatusData... params) {
            String statusStr = "";
            // Get the current state of scanner in background
            StatusData statusData = params[0];
            StatusData.ScannerStates state = statusData.getState();
            // Different states of Scanner
            switch (state) {
                // Scanner is IDLE
                case IDLE:
                    statusStr = "The scanner enabled and its idle";
                    break;
                // Scanner is SCANNING
                case SCANNING:
                    statusStr = "Scanning..";
                    break;
                // Scanner is waiting for trigger press
                case WAITING:
                    statusStr = "Waiting for trigger press..";
                    break;
                // Scanner is not enabled
                case DISABLED:
                    statusStr = "Scanner is not enabled";
                    break;
                default:
                    break;
            }

            // Return result to populate on UI thread
            return statusStr;
        }

        @Override
        protected void onPostExecute(String result) {
            // Update the status text view on UI thread with current scanner
            // state
            statusTextView.setText(result);
        }

        @Override
        protected void onPreExecute() {
        }

        @Override
        protected void onProgressUpdate(Void... values) {
        }
    }
    @Override
    protected void onDestroy(){
        super.onDestroy();
        emdkManager.release();
    }
    @Override
    public void onClosed() {


    }

    // Method to initialize and enable Scanner and its listeners
    private void initializeScanner() throws ScannerException {

        if (scanner == null) {

            // Get the Barcode Manager object
            barcodeManager = (BarcodeManager) this.emdkManager
                    .getInstance(EMDKManager.FEATURE_TYPE.BARCODE);

            // Get default scanner defined on the device
            scanner = barcodeManager.getDevice(BarcodeManager.DeviceIdentifier.DEFAULT);

            // Add data and status listeners
            scanner.addDataListener(this);
            scanner.addStatusListener(this);

            // Hard trigger. When this mode is set, the user has to manually
            // press the trigger on the device after issuing the read call.
            scanner.triggerType = Scanner.TriggerType.HARD;

            // Enable the scanner
            scanner.enable();

            // Starts an asynchronous Scan. The method will not turn ON the
            // scanner. It will, however, put the scanner in a state in which
            // the scanner can be turned ON either by pressing a hardware
            // trigger or can be turned ON automatically.
            scanner.read();
        }
    }
    public class RetrievePackageInfo extends AsyncTask<String, Void, JSONObject>{
        List<NameValuePair> postparams= new ArrayList<NameValuePair>();
        String URL=null;
        String method = null;
        public RetrievePackageInfo(String url, String method) {
            super();
            URL=url;
            this.method = method;
        }

        @Override
        protected void onPreExecute(){

            resultTextView.setText("...fetching data...");
        }
        @Override
        protected void onPostExecute(JSONObject result) {
            // Update the status text view on UI thread with current scanner
            // state
            String district ="";
            String route="";
            try {
                district=result.getString("district");
                route=result.getString("route");
            } catch (JSONException e) {
                e.printStackTrace();
            }


            resultTextView.setText("Market: Chicago\n" +
                    "District: " + district + "\n" +
                    "Route: " + route + "");
        }
        @Override
        protected JSONObject doInBackground(String... params) {
            // TODO Auto-generated method stub
            // Making HTTP request
            InputStream is = null;
            try {
                // Making HTTP request
                // check for request method

                String url=URL;
                if(method.equals("POST")){
                    // request method is POST
                    // defaultHttpClient
                    DefaultHttpClient httpClient = new DefaultHttpClient();
                    HttpPost httpPost = new HttpPost(url);
                    httpPost.setEntity(new UrlEncodedFormEntity(postparams));

                    HttpResponse httpResponse = httpClient.execute(httpPost);
                    HttpEntity httpEntity = httpResponse.getEntity();
                    is = httpEntity.getContent();

                }else if(method == "GET"){
                    // request method is GET
                    DefaultHttpClient httpClient = new DefaultHttpClient();
                    String paramString = URLEncodedUtils.format(postparams, "utf-8");
                    url += "?" + paramString;
                    HttpGet httpGet = new HttpGet(url);

                    HttpResponse httpResponse = httpClient.execute(httpGet);
                    HttpEntity httpEntity = httpResponse.getEntity();
                    is = httpEntity.getContent();
                }

            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            } catch (ClientProtocolException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            String json = null;
            JSONObject jObj = null;
            try {

                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        is, "iso-8859-1"), 8);
                StringBuilder sb = new StringBuilder();
                String line = null;
                while ((line = reader.readLine()) != null) {
                    sb.append(line + "\n");
                }
                is.close();
                json = sb.toString();
            } catch (Exception e) {
                Log.e("Buffer Error", "Error converting result " + e.toString());
            }

            // try parse the string to a JSON object
            try {
                jObj = new JSONObject(json);
            } catch (JSONException e) {
                Log.e("JSON Parser", "Error parsing data " + e.toString());
            }

            // return JSON String
            return jObj;

        }
    }
}


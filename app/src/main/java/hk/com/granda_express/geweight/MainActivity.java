package hk.com.granda_express.geweight;

import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.service.media.MediaBrowserService;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.DialogFragment;
import android.support.v4.media.MediaBrowserServiceCompat;
import android.support.v4.widget.TextViewCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.zxing.Result;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;

import me.dm7.barcodescanner.zxing.ZXingScannerView;

import static android.R.attr.id;

public class MainActivity extends AppCompatActivity
        implements ZXingScannerView.ResultHandler,
        OperatorFragment.OnFragmentInteractionListener {

    private final String GE_PREFS = "GE_PREFS";
    private final String KEY_OPERATOR = "operator";
    private final String KEY_LED = "led";

    EditText inputIDText;
    ZXingScannerView mScannerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        init();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        switch (id) {
            case R.id.action_user:
                // Create and show the dialog.
                DialogFragment userFragment = OperatorFragment.newInstance();
                userFragment.show(getSupportFragmentManager(), "operator_dialog");
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mScannerView != null) {
            mScannerView.setFlash(false);
            mScannerView.stopCamera();           // Stop camera on pause
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setTitle(R.string.app_name);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        SharedPreferences prefs = getSharedPreferences(GE_PREFS, MODE_PRIVATE);
        String userName = prefs.getString(KEY_OPERATOR, null);
        ((TextView) findViewById(R.id.textOperator)).setText(userName);
        boolean enableFlash = prefs.getBoolean(KEY_LED, false);
        ((CheckBox) findViewById(R.id.enableFlash)).setChecked(enableFlash);
    }

    @Override
    public void handleResult(Result rawResult) {
        setContentView(R.layout.activity_main);
        processOrderId(rawResult.getText());
    }

    public String getUserName() {
        TextView view = (TextView) findViewById(R.id.textOperator);
        return view.getText().toString();
    }

    public void setUserName(String userName) {
        TextView view = (TextView) findViewById(R.id.textOperator);
        view.setText(userName);
        SharedPreferences.Editor editor = getSharedPreferences(GE_PREFS, MODE_PRIVATE).edit();
        editor.putString(KEY_OPERATOR, userName);
        editor.commit();
    }

    private void init() {
        inputIDText = (EditText) findViewById(R.id.editText);
        inputIDText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                processOrderId(charSequence.toString());
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });
    }

    private void processOrderId(String id) {
        SharedPreferences prefs = getSharedPreferences(GE_PREFS, MODE_PRIVATE);
        String userName = prefs.getString(KEY_OPERATOR, null);
        ((TextView) findViewById(R.id.textOperator)).setText(userName);

        if (id.length() > 37) {
            String cartonNo = id.substring(37);
            String orderId = id.substring(0, 36);
            if (TextUtils.isEmpty(userName)) {
                Toast.makeText(getApplicationContext(), "请输入操作员名称", Toast.LENGTH_LONG).show();
            } else {
                new RetrieveOrder().execute(orderId, cartonNo);
            }
            inputIDText.setText("");
        }
    }

    public void checkLed(View view) {
        boolean flashEnabled = ((CheckBox) view).isChecked();
        SharedPreferences.Editor editor = getSharedPreferences(GE_PREFS, MODE_PRIVATE).edit();
        editor.putBoolean(KEY_LED, flashEnabled);
        editor.commit();
    }

    public void QrScanner(View view) {
        CheckBox flash = (CheckBox) findViewById(R.id.enableFlash);
        mScannerView = new ZXingScannerView(this);
        mScannerView.setFlash(flash.isChecked());
        setContentView(mScannerView);
        mScannerView.setResultHandler(this);
        mScannerView.startCamera();
    }

    class RetrieveOrder extends AsyncTask<String, Integer, String> {
        @Override
        protected String doInBackground(String... params) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    TextView messageView = (TextView) findViewById(R.id.messageView);
                    messageView.setText("请稍候，下载中　．．．");
                }
            });
            try {
                URL url = new URL(new ServiceUrl().REST_SERVICE_URL + "?orderid=" + params[0] + "&cartonno=" + params[1]);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                try {
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder stringBuilder = new StringBuilder();
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        stringBuilder.append(line).append("\n");
                    }
                    bufferedReader.close();
                    return stringBuilder.toString();
                } catch (Exception e) {
                    final String msg = e.getMessage().toString();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
                        }
                    });
                } finally {
                    connection.disconnect();
                }
            } catch (IOException e) {
                final String msg = e.getMessage().toString();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
                    }
                });
            }
            return null;
        }

        @Override
        protected void onPostExecute(final String result) {
            super.onPostExecute(result);
            if (result != null) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView messageView = (TextView) findViewById(R.id.messageView);
                        messageView.setText("");
                        Intent intent = new Intent(MainActivity.this, OrderActivity.class);
                        intent.putExtra("Order", result);
                        intent.putExtra("UserName", getUserName());
                        startActivity(intent);
                    }
                });
            }
        }
    }
}

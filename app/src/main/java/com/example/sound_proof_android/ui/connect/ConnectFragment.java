package com.example.sound_proof_android.ui.connect;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;

import android.preference.PreferenceManager;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.Editable;
import android.util.Base64;
import android.util.Log;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.sound_proof_android.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.spec.RSAKeyGenParameterSpec;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import static androidx.navigation.fragment.NavHostFragment.findNavController;

public class ConnectFragment extends Fragment {

    private ConnectViewModel connectViewModel;
    private Button qrButton;
    private Button loginButton;
    private EditText secretInput;
    private TextView statusText;

    SharedPreferences sharedPref;

    private String pubKey = "";
    private String code = "";
    // RSA variables
    private static final String TAG = "RSACryptor";
    private static final String CIPHER_ALGORITHM = "RSA/ECB/PKCS1Padding";
    private String test = "abcdefghijkhmlopqrstuvwxyz";
    byte[] encrypted;
    String decrypted;
    KeyPairGenerator keyPairGenerator;
    KeyPair kp;

    public static ConnectFragment newInstance() {
        return new ConnectFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_connect, container, false);

        qrButton = v.findViewById(R.id.qrButton);
        statusText = v.findViewById(R.id.statusText);
        loginButton = v.findViewById(R.id.loginButton);
        secretInput = v.findViewById(R.id.secretCodeInput);
        
        sharedPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String code = sharedPref.getString("enrollmentCode", "");
        if (code.equals("")) {
            statusText.setText("NOT ENROLLED");
            statusText.setTextColor(Color.RED);
        } else if (code.length() == 64) {
            statusText.setText("ENROLLED");
            statusText.setTextColor(Color.GREEN);
        }
        connectViewModel = new ViewModelProvider(requireActivity()).get(ConnectViewModel.class);

//        // TEST CODE TO CHECK IF BROWSER ENCRYPT WORKED BY DECRYPTING THE ENCRYPTED MESSAGE
//        String text = "YnDAnYk8yKfD7PN6yKV0zGgGrk1h291E74Gei0tFsfPYe3B08h7i2zS6HqBhQo1EjTbDWIOGdh6ESIZ2j6PCLxgqSujR+5ai43W4SQfdF8ygDCF5F+wQPHNQhLw0m9pDr4epZu17SujUbRsOHxVJK6BTQnyVJR4bhlex4gJ2RZbJrFVx7U7Ch+k34yEcoWlNc4HP8qgmteq/Cwd80qV6tVqRU6MeNv0WVKmLtuk9bbV2xmIZmQ99naLcZxoe3t6tDcMufH2vpuWNp8cFMpMBn6v2JBgmHP05hdcLdSz/DzrvnH/beLRPVRV/OuzMg+iTTIJue1Hje4DEo8xgEb50yA==";
//        byte[] decryptBytes = Base64.decode(text, Base64.DEFAULT);
//        Log.d(TAG,"DECRYPT "+ decrypt(decryptBytes));
//        // USE ABOVE CODE TO DECRYPT

        qrButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                NavController navController = findNavController(ConnectFragment.this);
                navController.navigate(R.id.action_nav_connect_to_nav_qrcode);
            }
        });
        
        loginButton.setOnClickListener(v1 -> {
            Editable secretCode = secretInput.getText();
            if (secretCode == null || secretCode.toString().equals("")) {
                Toast.makeText(requireContext(), "Secret code is empty!", Toast.LENGTH_SHORT).show();
            } else {
                if (secretCode.toString().length() == 64) {
                    connect(secretCode.toString());
                } else {
                    Toast.makeText(requireContext(), "Secret code is error!", Toast.LENGTH_SHORT).show();
                }
            }
        });

        connectViewModel.getSelectedCode().observe(getViewLifecycleOwner(), enrollCode -> {
            createKey();
            try {
                getPublicKey();
            } catch (KeyStoreException e) {
                e.printStackTrace();
            } catch (CertificateException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (UnrecoverableKeyException e) {
                e.printStackTrace();
            }
            connect(enrollCode);
        });

        return v;
    }

    public void connect(String code) {
        RequestQueue requestQueue = Volley.newRequestQueue(getActivity());
        String url = "https://sound-proof-web.herokuapp.com/tokenenrollment";

        SharedPreferences.Editor editor = sharedPref.edit();

        JSONObject postData = new JSONObject();
        try {
            postData.put("token", code);
            postData.put("key", pubKey.replaceAll("\n", ""));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        System.out.println(pubKey.replaceAll("\n", ""));
        String mRequestBody = postData.toString();

        StringRequest stringRequest = new StringRequest (Request.Method.POST, url, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
            if (response.equals("200")) {
                statusText.setText("ENROLLED");
                statusText.setTextColor(Color.GREEN);
                editor.putString("enrollmentCode", code);
                editor.apply();
                Toast.makeText(getActivity(), "Enrollment Success", Toast.LENGTH_LONG).show();
            }
        }
    }, new Response.ErrorListener() {
        @Override
        public void onErrorResponse(VolleyError error) {
            Log.e("LOG_RESPONSE", error.toString());
            statusText.setText("NOT ENROLLED");
            statusText.setTextColor(Color.RED);
            editor.putString("enrollmentCode", "");
            editor.apply();
            Toast.makeText(getActivity(), "Enrollment Failed", Toast.LENGTH_LONG).show();
        }
     }) {
            @Override
            public String getBodyContentType() {
                return "application/json; charset=utf-8";
            }

            @Override
            public byte[] getBody() throws AuthFailureError {
                try {
                    return mRequestBody == null ? null : mRequestBody.getBytes("utf-8");
                } catch (UnsupportedEncodingException uee) {
                    VolleyLog.wtf("Unsupported Encoding while trying to get the bytes of %s using %s", mRequestBody, "utf-8");
                    return null;
                }
            }

            @Override
            protected Response<String> parseNetworkResponse(NetworkResponse response) {
                String responseString = "";
                if (response != null) {
                    responseString = String.valueOf(response.statusCode);
                }
                return Response.success(responseString, HttpHeaderParser.parseCacheHeaders(response));
            }
        };

        requestQueue.add(stringRequest);
    }

    // server should make sure to use PKCS1 padding
    public void createKey(){
        try {
            keyPairGenerator = KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore");
            keyPairGenerator.initialize(
                    new KeyGenParameterSpec.Builder(
                            "spKey",
                            KeyProperties.PURPOSE_DECRYPT | KeyProperties.PURPOSE_ENCRYPT)
                            .setAlgorithmParameterSpec(new RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4))
                            .setDigests(KeyProperties.DIGEST_SHA256 , KeyProperties.DIGEST_SHA512)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                            .build());
            kp = keyPairGenerator.generateKeyPair();
        }
        catch (Exception e){
            Log.e(TAG, ""+e);
        }
    }

    // displays the public key of the phone
    // if the keystore is empty, both public and private key is created
    public void getPublicKey() throws KeyStoreException, CertificateException, IOException, NoSuchAlgorithmException, UnrecoverableKeyException {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (!keyStore.containsAlias("spKey")) {
            createKey();
        }
        PublicKey publicKey = keyStore.getCertificate("spKey").getPublicKey();
        pubKey = Base64.encodeToString(publicKey.getEncoded(), Base64.DEFAULT);
    }

    public String decrypt(final byte[] encryptedText){
        try{
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            PrivateKey privateKey = (PrivateKey) keyStore.getKey("spKey", null);
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE,privateKey);
            byte[] decryptedText = cipher.doFinal(encryptedText);
            Log.d(TAG, "end decrypt" + decryptedText.toString());
            return new String(decryptedText);
        }catch (UnrecoverableKeyException | IOException | KeyStoreException | CertificateException | NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | BadPaddingException | IllegalBlockSizeException e){
            Log.e("decrypt catch", e.getMessage()+"");
            e.printStackTrace();
            return null;
        }

    }

    // temp block of code: app does not need to encrypt
    public byte[] encrypt(final String text) throws NoSuchAlgorithmException, NoSuchPaddingException,
            InvalidKeyException, IllegalBlockSizeException, BadPaddingException, KeyStoreException, CertificateException, IOException {
        Log.d(TAG, "encrypt test" + text+"");
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        PublicKey publicKey = keyStore.getCertificate("spKey").getPublicKey();
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);

        Log.d(TAG,"publicKey1"+publicKey);
        String pubkey3 = publicKey.getEncoded().toString();
        Log.d(TAG,"publicKey3"+ pubkey3);
        Log.d(TAG,"publicKey2"+Base64.encodeToString(publicKey.getEncoded(), Base64.DEFAULT));
        cipher.init(Cipher.ENCRYPT_MODE,publicKey);

        byte[] publicKeyBytes = Base64.encode(publicKey.getEncoded(),0);
        String pubKey = new String(publicKeyBytes);
        Log.d(TAG,"publicKey4444"+ pubkey3);

        byte[] encryptedBytes = cipher.doFinal(text.getBytes("utf-8"));
        Log.d(TAG, "encry"+encryptedBytes.toString());
        return encryptedBytes;
    }
}
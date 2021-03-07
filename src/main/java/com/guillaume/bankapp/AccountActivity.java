package com.guillaume.bankapp;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import android.widget.TextView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import java.security.InvalidKeyException;
import java.security.Key;

import java.security.NoSuchAlgorithmException;


import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;


public class AccountActivity extends AppCompatActivity {

    //The TextView of the activity
    private TextView mTextView;

    //Request queue for https requests used with Volley
    private RequestQueue mQueue;
    private Context context = this;

    //The key to encrypt datas
    private String secretKey = "à&g'ROwC9x|VjtcW5&RDh@6RmD^E|pEN";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);


        mTextView = findViewById(R.id.text_view_result);


        mQueue = Volley.newRequestQueue(this);

        try {
            jsonParse();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


    //Encrypt-Decrypt file with AES (Advanced Encryption Standard)
    private static void doCryptoInAES(int cipherMode, String key, File inputFile,
                                      File outputFile){
        try {
            Key secretKey = new SecretKeySpec(key.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(cipherMode, secretKey);

            FileInputStream inputStream = new FileInputStream(inputFile);
            byte[] inputBytes = new byte[(int) inputFile.length()];
            inputStream.read(inputBytes);

            byte[] outputBytes = cipher.doFinal(inputBytes);

            FileOutputStream outputStream = new FileOutputStream(outputFile);
            outputStream.write(outputBytes);

            inputStream.close();
            outputStream.close();

        } catch (NoSuchPaddingException | NoSuchAlgorithmException
                | InvalidKeyException | BadPaddingException
                | IllegalBlockSizeException | IOException ex) {
            ex.printStackTrace();
        }
    }


    //Withdraw information on the API or in internal storage depending on Internet connection
    private void jsonParse() throws IOException, JSONException {
        final String accountId = getIntent().getStringExtra("ID");
        String url = "https://60102f166c21e10017050128.mockapi.io/labbbank/accounts/" + accountId;

        //The file is stored in internal storage, so only accessible from this application
        final File file = new File(context.getFilesDir(), "accounts.json");
        FileWriter fileWriter = null;
        FileReader fileReader= null;

        BufferedWriter bufferedWriter = null;
        BufferedReader bufferedReader = null;

        String content = null;


        // Check if the file where we store bank data exists already, (we will create it only the first use of the app)
        if (!file.exists()){
            try{
                file.createNewFile();
                fileWriter = new FileWriter(file.getAbsoluteFile());
                bufferedWriter = new BufferedWriter(fileWriter);
                bufferedWriter.write("{ \"accounts\" : []}");
                bufferedWriter.close();
            }catch (IOException e){
                e.printStackTrace();
            }
        }
        else{
            //If file already exist, we decrypt it
            doCryptoInAES(Cipher.DECRYPT_MODE,secretKey,file,file);
        }

        //Read The file
        StringBuffer output = new StringBuffer();
        fileReader = new FileReader(file.getAbsolutePath());
        bufferedReader = new BufferedReader(fileReader);

        String line = "";

        while((line = bufferedReader.readLine()) != null){
            output.append(line + "\n");
        }

        content = output.toString();
        bufferedReader.close();

        final JSONObject fileJSON = new JSONObject(content);

        //We try to get data from mockapi
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    try {
                        //Save info in internal storage and display it

                        String id = response.getString("id");
                        String accountName = response.getString("accountName");
                        String amount = response.getString("amount");
                        String iban = response.getString("iban");
                        String currency = response.getString("currency");

                        //Update the TextView
                        mTextView.append(accountName + "\n");
                        mTextView.append("Amount : " +amount + " " + currency + "\n");
                        mTextView.append("Iban : " + iban);

                        //update the file
                        fileJSON.put(accountId,response);

                        FileWriter fileWriter1 = new FileWriter(file.getAbsoluteFile());
                        BufferedWriter bufferedWriter1 = new BufferedWriter(fileWriter1);
                        bufferedWriter1.write(fileJSON.toString());
                        bufferedWriter1.close();

                        //Encrypt the file
                        doCryptoInAES(Cipher.ENCRYPT_MODE, secretKey, file, file);

                    } catch (JSONException | IOException e) {
                        e.printStackTrace();
                    }
                }
            }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.d("REQUEST","no internet");

                try {
                    //Display info from the internal storage
                    JSONObject response2 = fileJSON.getJSONObject(accountId);

                    String id = response2.getString("id");
                    String accountName = response2.getString("accountName");
                    String amount = response2.getString("amount");
                    String iban = response2.getString("iban");
                    String currency = response2.getString("currency");

                    mTextView.append(accountName + "\n");
                    mTextView.append("Amount : " +amount + " " + currency + "\n");
                    mTextView.append("Iban : " + iban);

                //If the information is not stored locally
                } catch (JSONException e) {
                    //e.printStackTrace();

                    mTextView.setText("Sorry you don't have local saves for this account");
                }

            }
        });

        mQueue.add(request);
    }

    //A simple refresh button
    public void Refresh(View view){
        finish();
        startActivity(getIntent());
    }
}

package com.guillaume.bankapp;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;


public class MainActivity extends AppCompatActivity {

    //The spinner that display the accounts availables
    private Spinner spinner;

    //Concellation signal for the Biometric functionality
    private CancellationSignal cancellationSignal = null;

    //Request queue for https requests used with Volley
    private RequestQueue mQueue;

    private Context context = this;

    // variables used to update dynamically the spinner
    public static ArrayList<String> spinnerArray;
    public static ArrayAdapter<String> spinnerAdapter;

    //corresponding map between accountNames and Id
    private static HashMap<String, Integer> NameToId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        spinner = findViewById(R.id.spinner);


        NameToId = new HashMap<String, Integer>();

        spinnerArray = new ArrayList<String>();
        spinnerAdapter = new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item, spinnerArray);
        spinner.setAdapter(spinnerAdapter);
        spinner.setVisibility(View.VISIBLE);
        mQueue = Volley.newRequestQueue(this);

        try {
            jsonParse();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Button findButton = findViewById(R.id.findButton);

        final Executor executor = Executors.newSingleThreadExecutor();


        //Biometric check before displaying accounts infos
        final BiometricPrompt biometricPrompt = new BiometricPrompt.Builder(this)
                .setTitle("Fingerprint Authentification")
                .setDescription("Please authenticate to see your account")
                .setNegativeButton("Cancel", executor, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                }).build();

        findButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                biometricPrompt.authenticate(new CancellationSignal(), executor, new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);

                        String idText = spinner.getSelectedItem().toString();
                        Intent accountIntent = new Intent(getBaseContext(),AccountActivity.class);
                        int id = NameToId.get(idText);
                        Log.d("REQUEST", String.valueOf(id));
                        //Four the next activity we need to know the account id to be able to connect to the correct url
                        accountIntent.putExtra("ID", String.valueOf(id));
                        startActivity(accountIntent);
                    }
                });

            }
        });


    }

    //Withdraw information on the API or in internal storage depending on Internet connection
    private void jsonParse() throws IOException, JSONException {
        String url = "https://60102f166c21e10017050128.mockapi.io/labbbank/accounts/";

        //The file is stored in internal storage, so only accessible from this application
        //As it only contains account names and no critical information, i didn't encrypt it
        final File file = new File(context.getFilesDir(), "accountNames.json");
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
                bufferedWriter.write("{}");
                bufferedWriter.close();
            }catch (IOException e){
                e.printStackTrace();
            }
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
        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, url, null,
                new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        try {
                            //Save info in internal storage and display it*
                            //Iterate over every account to add its name in our spinner and our file
                            for (int i = 0; i < response.length(); i++){
                                JSONObject accountJSON = response.getJSONObject(i);
                                String accountName = accountJSON.getString("accountName");
                                String idS = accountJSON.getString("id");
                                Log.d("REQUEST",accountName);
                                int id = Integer.parseInt(idS);

                                //Add to the spinner
                                spinnerArray.add(accountName);
                                //Add the corresponding id to this account name, we will need this id for the next activity
                                NameToId.put(accountName,id);
                                //Add to the file
                                fileJSON.put(String.valueOf(i),accountName);

                            }
                            //update the spinner
                            spinnerAdapter.notifyDataSetChanged();


                            FileWriter fileWriter1 = new FileWriter(file.getAbsoluteFile());
                            BufferedWriter bufferedWriter1 = new BufferedWriter(fileWriter1);
                            bufferedWriter1.write(fileJSON.toString());
                            bufferedWriter1.close();


                        } catch (IOException | JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                //If we don't manage to connect to the API we go to onErrorResponse
                Log.d("REQUEST","no internet");

                try {
                    //We will check our file with the name of ou accounts,
                    //As we don't know the number of accounts, we will continue until an error
                    int id = 1;
                    while(1==1){
                        String accountName = fileJSON.getString(String.valueOf(id));
                        spinnerArray.add(accountName);
                        NameToId.put(accountName,id);
                        id++;
                    }
                    
                    
                } catch (JSONException e) {
                    //The error might mean the end of the list so we update our arrayAdapter for the spinner
                    e.printStackTrace();
                    spinnerAdapter.notifyDataSetChanged();
                    Log.d("REQUEST", "End of list");
                    
                }

            }
        });

        mQueue.add(request);
    }

}

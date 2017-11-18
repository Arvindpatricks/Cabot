package com.hackadroid.cabot;


import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.hackadroid.datamodel.UserDataModel;

import static com.hackadroid.datamodel.CabotDataModelConstants.TABLE_USERS;

/**
 * A login screen that uses FireBase Login via GoogleSignIn
 */

public class LoginActivity extends AppCompatActivity implements GoogleApiClient.OnConnectionFailedListener , View.OnClickListener {

    private static final int RC_SIGN_IN = 9001;
    private static final String TAG = "LOGIN_ACTIVITY";

    private SignInButton signInButton;
    private GoogleApiClient mGoogleApiClient;
    private FirebaseAuth firebaseAuth;
    private Animation fadeIn;
    private ImageView imageView;
    private FirebaseFirestore firebaseFirestore;
    private boolean existingUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_login);

        //Initializing the Google & Firestore dependencies
        firebaseAuth = FirebaseAuth.getInstance();
        GoogleSignInOptions googleSignInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestIdToken(getString(R.string.default_web_client_id))
                .build();

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this, this)
                .addApi(Auth.GOOGLE_SIGN_IN_API, googleSignInOptions)
                .build();

        firebaseFirestore = FirebaseFirestore.getInstance();

        //Initializing the UI Components to be accessed
        signInButton = (SignInButton) findViewById(R.id.sign_in_button);
        signInButton.setOnClickListener(this);
        imageView = (ImageView) findViewById(R.id.logo);
        fadeIn = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.fade_in);

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.e(TAG, String.format("GoogleApiClient connection failed !! : %s", connectionResult));
    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {

    }

    @Override
    public void onClick(View view) {
        //TODO : Once we have authentication using FB, should add handlers for it here
        switch (view.getId()) {
            case R.id.sign_in_button:
                googlesignIn();
                break;
        }
    }

    private void googlesignIn() {
        Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                //Google sign in was successful, authenticate with Firebase
                GoogleSignInAccount account = task.getResult(ApiException.class);
                firebaseAuthWithGoogle(account);
            } catch (ApiException e) {
                Log.e(TAG, "Google Authentication Failed", e);
                Toast.makeText(LoginActivity.this, "Google Authentication Failed ! Try again later !!", Toast.LENGTH_SHORT).show();

            }
        }
    }

    //Logic for checking if the user is already authenticated
    @Override
    public void onStart() {
        super.onStart();
        //Check if user is signed in and update the UI
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser != null) {

            imageView.startAnimation(fadeIn);

            Thread timerThread = new Thread() {
                public void run() {
                    try {
                        sleep(2000);
                    } catch (InterruptedException e) {
                        Log.e(TAG,"Error while sleeping before opening the MainActivity !", e);
                    } finally {
                        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                        startActivity(intent);
                    }
                }
            };
            timerThread.start();
        } else {
            Log.i(TAG,"FirebaseUser is null !!");
        }

    }

    private void firebaseAuthWithGoogle(GoogleSignInAccount account) {
        Log.d(TAG, String.format("Authenticating the Google User with Firebase : %s", account.getId()));

        AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
        firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {

                            //Sign in success, update UI with signed in user
                            Toast.makeText(LoginActivity.this, "Signed In !!", Toast.LENGTH_SHORT).show();
                            Log.i(TAG, "firebaseAuthWithGoogle : Success");
                            FirebaseUser user = firebaseAuth.getCurrentUser();

                            checkIfNewUser(user);

                            imageView.startAnimation(fadeIn);
                            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                            startActivity(intent);

                        } else {
                            Log.i(TAG, "SigninFirebsase : Failure");
                            Toast.makeText(LoginActivity.this, "Cabot Login failed !! Try again later !!", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    /**
     * Verifies if the User is already present in the _Users Table
     * @param user
     * @return true - if the user exists
     */
    private void checkIfNewUser(@NonNull final FirebaseUser user) {

        DocumentReference docRef = firebaseFirestore.collection(TABLE_USERS).document(user.getUid());
        docRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                if (task.isSuccessful()) {
                    DocumentSnapshot document = task.getResult();
                    if(document.exists()) {
                        Log.d(TAG, "DocumentSnapshot data: " + task.getResult().getData());
                    } else {
                        Log.d(TAG, "No such document");
                        addNewUserInFirebase(user);
                    }
                } else {
                    Log.d(TAG, "get failed with ", task.getException());
                }
            }
        });
    }

    /**
     * Inserts the User data in the Firebase _Users Table
     * @param user
     */
    private void addNewUserInFirebase(FirebaseUser user) {
        Log.i(TAG,"Inserting data for new User : "+user.getDisplayName());
        UserDataModel userDataModel = new UserDataModel();
        userDataModel.set_userId(user.getUid());
        userDataModel.set_emailId(user.getEmail());
        userDataModel.set_FullName(user.getDisplayName());
        Log.d(TAG, "Updating the data : "+ userDataModel.toString());
        firebaseFirestore.collection(TABLE_USERS).document(user.getUid()).set(userDataModel)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Toast.makeText(LoginActivity.this, "Let's book a ride !!", Toast.LENGTH_SHORT).show();
                    }
                })

                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(LoginActivity.this, "Unable to register the User", Toast.LENGTH_SHORT).show();
                        Log.d("TAG", e.toString());
                    }
                });
    }
}


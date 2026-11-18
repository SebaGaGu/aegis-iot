package com.example.aegis;

import android.content.Intent;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.util.Log;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;

public class LoginActivity extends AppCompatActivity {

    // Elementos de la pantalla
    private EditText etCorreo, etContrasena;
    private Button btnIniciarSesion, btnGoogle;
    private TextView tvRegistrarse;

    // Firebase Authentication
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Busco los elementos de mi XML
        etCorreo = findViewById(R.id.etCorreo);
        etContrasena = findViewById(R.id.etContrasena);
        btnIniciarSesion = findViewById(R.id.btnIniciarSesion);
        btnGoogle = findViewById(R.id.btnGoogle);
        tvRegistrarse = findViewById(R.id.tvRegistrarse);

        // Inicializo Firebase
        auth = FirebaseAuth.getInstance();

        // Boton para iniciar sesion con correo
        btnIniciarSesion.setOnClickListener(v -> iniciarSesion());

        // Boton para iniciar sesion con Google
        btnGoogle.setOnClickListener(v -> iniciarSesionGoogle());

        // Enlace para abrir el registro de Sebastian
        tvRegistrarse.setOnClickListener(v -> {

            Intent intent = new Intent(
                    LoginActivity.this,
                    RegistroActivity.class
            );

            startActivity(intent);
        });
    }


    // =========================================
    // INICIO DE SESION CON CORREO Y CONTRASEÑA
    // =========================================

    private void iniciarSesion() {

        // Recojo los datos que he introducido
        String correo = etCorreo.getText().toString().trim();
        String contrasena = etContrasena.getText().toString();

        // Compruebo si el correo esta vacio
        if (correo.isEmpty()) {
            etCorreo.setError("Introduce tu correo");
            etCorreo.requestFocus();
            return;
        }

        // Compruebo si el formato del correo es valido
        if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            etCorreo.setError("Correo electrónico no válido");
            etCorreo.requestFocus();
            return;
        }

        // Compruebo si falta la contraseña
        if (contrasena.isEmpty()) {
            etContrasena.setError("Introduce tu contraseña");
            etContrasena.requestFocus();
            return;
        }

        // Desactivo el boton mientras compruebo los datos
        btnIniciarSesion.setEnabled(false);

        // Intento iniciar sesion mediante Firebase
        auth.signInWithEmailAndPassword(correo, contrasena)
                .addOnCompleteListener(this, task -> {

                    btnIniciarSesion.setEnabled(true);

                    if (task.isSuccessful()) {

                        // Si funciona, entro en la aplicacion
                        abrirPantallaPrincipal(
                                "Sesión iniciada correctamente"
                        );

                    } else {

                        // Si no funciona, muestro un error
                        Toast.makeText(
                                LoginActivity.this,
                                "No se pudo iniciar sesión. Revisa tus datos.",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
    }


    // =========================================
    // INICIO DE SESION CON GOOGLE
    // =========================================

    private void iniciarSesionGoogle() {

        // Busco el identificador web que genera Firebase
        int idRecurso = getResources().getIdentifier(
                "default_web_client_id",
                "string",
                getPackageName()
        );

        // Compruebo que exista antes de continuar
        if (idRecurso == 0) {

            Toast.makeText(
                    this,
                    "Falta configurar el ID de cliente web de Google",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        // Obtengo el identificador real de Firebase
        String clientId = getString(idRecurso);

        // Compruebo que tenga contenido
        if (clientId.trim().isEmpty()) {

            Toast.makeText(
                    this,
                    "El ID de cliente web está vacío",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        // Desactivo el boton mientras abro Google
        btnGoogle.setEnabled(false);

        // Configuro el inicio de sesion con Google
        GetSignInWithGoogleOption opcionGoogle =
                new GetSignInWithGoogleOption.Builder(
                        clientId
                ).build();

        // Preparo la solicitud
        GetCredentialRequest solicitud =
                new GetCredentialRequest.Builder()
                        .addCredentialOption(opcionGoogle)
                        .build();

        // Inicializo Credential Manager
        CredentialManager gestor =
                CredentialManager.create(this);

        // Abro la seleccion de cuentas de Google
        gestor.getCredentialAsync(
                this,
                solicitud,
                new CancellationSignal(),
                ContextCompat.getMainExecutor(this),

                new CredentialManagerCallback<
                        GetCredentialResponse,
                        GetCredentialException>() {

                    @Override
                    public void onResult(
                            @NonNull GetCredentialResponse resultado) {

                        btnGoogle.setEnabled(true);

                        // Obtengo la credencial seleccionada
                        Credential credencial =
                                resultado.getCredential();

                        // Compruebo que sea una credencial de Google
                        if (credencial instanceof CustomCredential
                                && GoogleIdTokenCredential
                                .TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                                .equals(credencial.getType())) {

                            try {

                                // Obtengo el token de Google
                                GoogleIdTokenCredential cuentaGoogle =
                                        GoogleIdTokenCredential.createFrom(
                                                ((CustomCredential) credencial)
                                                        .getData()
                                        );

                                // Envio el token a Firebase
                                autenticarGoogleEnFirebase(
                                        cuentaGoogle.getIdToken()
                                );

                            } catch (IllegalArgumentException e) {

                                // Si el token no se puede leer
                                Log.e(
                                        "AEGIS",
                                        "Error al leer el token de Google",
                                        e
                                );

                                Toast.makeText(
                                        LoginActivity.this,
                                        "Error al leer la cuenta de Google",
                                        Toast.LENGTH_LONG
                                ).show();
                            }

                        } else {

                            // Si recibo una credencial desconocida
                            Toast.makeText(
                                    LoginActivity.this,
                                    "Credencial de Google no válida",
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    }

                    @Override
                    public void onError(
                            @NonNull GetCredentialException error) {

                        btnGoogle.setEnabled(true);

                        // Compruebo si he cancelado el inicio
                        if (error instanceof
                                GetCredentialCancellationException) {

                            Toast.makeText(
                                    LoginActivity.this,
                                    "Inicio de sesión cancelado",
                                    Toast.LENGTH_SHORT
                            ).show();

                        } else {

                            // Si falla Google, muestro el error
                            Log.e(
                                    "AEGIS",
                                    "Error al iniciar sesión con Google",
                                    error
                            );

                            Toast.makeText(
                                    LoginActivity.this,
                                    "No se pudo iniciar sesión con Google",
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    }
                }
        );
    }


    // =========================================
    // AUTENTICAR GOOGLE MEDIANTE FIREBASE
    // =========================================

    private void autenticarGoogleEnFirebase(String tokenGoogle) {

        // Convierto el token de Google en credencial Firebase
        AuthCredential credencialFirebase =
                GoogleAuthProvider.getCredential(
                        tokenGoogle,
                        null
                );

        // Inicio sesion con esa credencial
        auth.signInWithCredential(credencialFirebase)
                .addOnCompleteListener(this, task -> {

                    if (task.isSuccessful()) {

                        // Si Firebase acepta la cuenta, entro en AEGIS
                        abrirPantallaPrincipal(
                                "Sesión iniciada con Google"
                        );

                    } else {

                        // Si Firebase falla, muestro el error
                        Log.e(
                                "AEGIS",
                                "Error de Firebase con Google",
                                task.getException()
                        );

                        Toast.makeText(
                                LoginActivity.this,
                                "Error al autenticar con Firebase",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
    }

    // =========================================
    // ABRIR LA PANTALLA DE MI CUENTA
    // =========================================

    private void abrirPantallaPrincipal(String mensaje) {

        // Muestro que he iniciado sesion correctamente
        Toast.makeText(
                this,
                mensaje,
                Toast.LENGTH_SHORT
        ).show();

        // Abro la pantalla de mi cuenta
        Intent intent = new Intent(
                LoginActivity.this,
                CuentaActivity.class
        );

        startActivity(intent);

        // Cierro el login para no volver con Atras
        finish();
    }

}

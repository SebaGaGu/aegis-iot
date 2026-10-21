package com.example.aegis;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class LoginActivity extends AppCompatActivity {

    private EditText etCorreo, etContrasena;
    private Button btnIniciarSesion;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Conecto los elementos de mi pantalla
        etCorreo = findViewById(R.id.etCorreo);
        etContrasena = findViewById(R.id.etContrasena);
        btnIniciarSesion = findViewById(R.id.btnIniciarSesion);

        // Inicializo Firebase Authentication
        auth = FirebaseAuth.getInstance();

        // Cuando pulso el boton, intento iniciar sesion
        btnIniciarSesion.setOnClickListener(v -> iniciarSesion());
    }

    private void iniciarSesion() {

        // Recojo el correo y la contraseña que he escrito
        String correo = etCorreo.getText().toString().trim();
        String contrasena = etContrasena.getText().toString();

        // Compruebo que el correo no este vacio
        if (correo.isEmpty()) {
            etCorreo.setError("Introduce tu correo");
            etCorreo.requestFocus();
            return;
        }

        // Compruebo que tenga formato de correo
        if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            etCorreo.setError("Correo electronico no valido");
            etCorreo.requestFocus();
            return;
        }

        // Compruebo que haya introducido la contraseña
        if (contrasena.isEmpty()) {
            etContrasena.setError("Introduce tu contraseña");
            etContrasena.requestFocus();
            return;
        }

        // Desactivo el boton mientras Firebase comprueba los datos
        btnIniciarSesion.setEnabled(false);

        // Intento iniciar sesion con Firebase
        auth.signInWithEmailAndPassword(correo, contrasena)
                .addOnCompleteListener(this, task -> {

                    btnIniciarSesion.setEnabled(true);

                    if (task.isSuccessful()) {

                        // Si los datos son correctos, entro en la app
                        Toast.makeText(this,
                                "Sesión iniciada correctamente",
                                Toast.LENGTH_SHORT).show();

                        Intent intent = new Intent(
                                LoginActivity.this,
                                MainActivity.class);

                        startActivity(intent);
                        finish();

                    } else {

                        // Si los datos son incorrectos, muestro un error
                        Toast.makeText(this,
                                "No se pudo iniciar sesión. Revisa tus datos.",
                                Toast.LENGTH_LONG).show();
                    }
                });
    }
}

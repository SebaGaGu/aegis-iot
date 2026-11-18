
package com.example.aegis;

import android.os.Bundle;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class RegistroActivity extends AppCompatActivity {

    private EditText etCorreo, etContrasena, etRepetir;
    private Button btnCrearCuenta;
    private TextView tvIrLogin;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registro);

        // Conectamos los elementos de la pantalla
        etCorreo = findViewById(R.id.etCorreoRegistro);
        etContrasena = findViewById(R.id.etContrasenaRegistro);
        etRepetir = findViewById(R.id.etRepetirContrasena);
        btnCrearCuenta = findViewById(R.id.btnCrearCuenta);
        tvIrLogin = findViewById(R.id.tvIrLogin);

        // Inicializamos Firebase Authentication
        auth = FirebaseAuth.getInstance();

        // Boton para crear una nueva cuenta
        btnCrearCuenta.setOnClickListener(v -> crearCuenta());

        // Volvemos a la pantalla de inicio de sesion
        tvIrLogin.setOnClickListener(v -> finish());
    }

    private void crearCuenta() {

        // Recogemos los datos introducidos
        String correo = etCorreo.getText().toString().trim();
        String contrasena = etContrasena.getText().toString();
        String repetir = etRepetir.getText().toString();

        // Comprobamos que se haya escrito el correo
        if (correo.isEmpty()) {
            etCorreo.setError("Introduce tu correo");
            etCorreo.requestFocus();
            return;
        }

        // Comprobamos el formato del correo
        if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            etCorreo.setError("Correo electronico no valido");
            etCorreo.requestFocus();
            return;
        }

        // Comprobamos que haya una contraseña
        if (contrasena.isEmpty()) {
            etContrasena.setError("Introduce una contraseña");
            etContrasena.requestFocus();
            return;
        }

        // Firebase requiere al menos 6 caracteres
        if (contrasena.length() < 6) {
            etContrasena.setError(
                    "La contraseña debe tener al menos 6 caracteres");
            etContrasena.requestFocus();
            return;
        }

        // Comprobamos que las contraseñas coincidan
        if (!contrasena.equals(repetir)) {
            etRepetir.setError("Las contraseñas no coinciden");
            etRepetir.requestFocus();
            return;
        }

        // Desactivamos el boton durante el registro
        btnCrearCuenta.setEnabled(false);

        // Creamos el usuario en Firebase Authentication
        auth.createUserWithEmailAndPassword(correo, contrasena)
                .addOnCompleteListener(this, task -> {

                    btnCrearCuenta.setEnabled(true);

                    if (task.isSuccessful()) {

                        Toast.makeText(this,
                                "Cuenta creada correctamente",
                                Toast.LENGTH_SHORT).show();

                        // Firebase inicia sesion al registrar.
                        // La cerramos para volver al login.
                        auth.signOut();

                        // Regresamos a LoginActivity
                        finish();

                    } else {

                        Toast.makeText(this,
                                "No se pudo crear la cuenta. " +
                                        "Comprueba los datos o si ya existe.",
                                Toast.LENGTH_LONG).show();
                    }
                });
    }
}

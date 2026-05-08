package com.example.cradet

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.cradet.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authManager = AuthManager(this)

        if (authManager.isLoggedIn()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val pass = binding.etPassword.text.toString().trim()

            android.util.Log.d("LoginActivity", "Login button clicked. Email: $email")

            if (email.isNotEmpty() && pass.isNotEmpty()) {
                if (authManager.login(email, pass)) {
                    android.util.Log.d("LoginActivity", "Login success. Redirecting to MainActivity")
                    authManager.setLoggedIn(email)
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    android.util.Log.d("LoginActivity", "Login failed. Invalid credentials")
                    Toast.makeText(this, "Invalid credentials", Toast.LENGTH_SHORT).show()
                }
            } else {
                android.util.Log.d("LoginActivity", "Login failed. Fields empty")
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            }
        }

        binding.tvGoToSignUp.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }
    }
}

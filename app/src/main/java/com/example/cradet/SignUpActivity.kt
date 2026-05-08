package com.example.cradet

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.cradet.databinding.ActivitySignupBinding

class SignUpActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySignupBinding
    private lateinit var authManager: AuthManager
    private lateinit var profileManager: ProfileManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authManager = AuthManager(this)
        profileManager = ProfileManager(this)

        binding.btnSignUp.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val pass = binding.etPassword.text.toString().trim()
            val blood = binding.etBlood.text.toString().trim()
            val allergies = binding.etAllergies.text.toString().trim()

            android.util.Log.d("SignUpActivity", "SignUp button clicked. Email: $email")

            if (name.isNotEmpty() && email.isNotEmpty() && pass.isNotEmpty()) {
                if (authManager.signUp(email, pass)) {
                    android.util.Log.d("SignUpActivity", "SignUp success for $email. Saving profile and auto-logging in.")
                    profileManager.saveProfile(name, blood, allergies)
                    
                    // Auto-login after sign up
                    authManager.setLoggedIn(email)
                    
                    Toast.makeText(this, "Account created successfully", Toast.LENGTH_SHORT).show()
                    
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    android.util.Log.d("SignUpActivity", "SignUp failed. Email $email already registered.")
                    Toast.makeText(this, "Email already registered", Toast.LENGTH_SHORT).show()
                }
            } else {
                android.util.Log.d("SignUpActivity", "SignUp failed. Required fields empty.")
                Toast.makeText(this, "Please fill required fields", Toast.LENGTH_SHORT).show()
            }
        }

        binding.tvGoToLogin.setOnClickListener {
            finish()
        }
    }
}

package com.example.railtap

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth

class SignUpActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        auth = FirebaseAuth.getInstance()

        val backButton = findViewById<ImageButton>(R.id.backButton)

        //for the signup event to
        val fullNameInput = findViewById<TextInputEditText>(R.id.fullNameInput)
        val emailInput = findViewById<TextInputEditText>(R.id.emailInput)
        val passwordInput = findViewById<TextInputEditText>(R.id.passwordInput)
        val confirmPasswordInput = findViewById<TextInputEditText>(R.id.confirmPasswordInput)
        val signUpButton = findViewById<Button>(R.id.signUpButton)

        //the back button
        backButton.setOnClickListener {
            val intent = Intent(this, LogInActivity::class.java)
            startActivity(intent)
        }


        //the signup logic

        signUpButton.setOnClickListener {
            val fullName = fullNameInput.text.toString().trim()
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            val confirmPassword = confirmPasswordInput.text.toString().trim()

            if (fullName.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // user info to firebase
            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                        .setDisplayName(fullName)
                        .build()

                    result.user?.updateProfile(profileUpdates)

                    Toast.makeText(this, "Account created!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, DashboardActivity::class.java))
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Sign up failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }
}
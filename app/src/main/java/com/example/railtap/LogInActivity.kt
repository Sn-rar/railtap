package com.example.railtap

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth

class LogInActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        // 1. Find the views by their IDs from the XML layout
        val emailInput = findViewById<TextInputEditText>(R.id.emailInput)
        val passwordInput = findViewById<TextInputEditText>(R.id.passwordInput)
        val signInButton = findViewById<Button>(R.id.signInButton)
        val signUpLink = findViewById<TextView>(R.id.signUpLink)

        // 2. Set click listener for the Sign In button
        signInButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    Toast.makeText(this, "Welcome back!", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, DashboardActivity::class.java)
                    startActivity(intent)
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Sign in failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }

        // 3. Set click listener for the Sign Up text at the bottom
        signUpLink.setOnClickListener {
            val intent = Intent(this, SignUpActivity::class.java)
            startActivity(intent)
        }
    }
}
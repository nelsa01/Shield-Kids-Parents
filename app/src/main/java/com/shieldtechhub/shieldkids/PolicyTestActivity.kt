package com.shieldtechhub.shieldkids

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shieldtechhub.shieldkids.databinding.ActivityPolicyTestBinding
import com.shieldtechhub.shieldkids.features.policy.test.PolicyEnforcementTester
import kotlinx.coroutines.launch

/**
 * Activity for testing policy enforcement functionality
 * This should only be available in debug builds or for testing purposes
 */
class PolicyTestActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "PolicyTestActivity"
    }
    
    private lateinit var binding: ActivityPolicyTestBinding
    private lateinit var policyTester: PolicyEnforcementTester
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPolicyTestBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        initializeTester()
    }
    
    private fun setupUI() {
        binding.toolbarTitle.text = "Policy Enforcement Testing"
        
        binding.btnRunAllTests.setOnClickListener {
            runAllTests()
        }
        
        binding.btnRunBasicTests.setOnClickListener {
            runBasicTests()
        }
        
        binding.btnRunStressTests.setOnClickListener {
            runStressTests()
        }
        
        binding.btnClearResults.setOnClickListener {
            clearTestResults()
        }
        
        binding.btnBack.setOnClickListener {
            finish()
        }
    }
    
    private fun initializeTester() {
        try {
            policyTester = PolicyEnforcementTester.createTestInstance(this)
            binding.tvStatus.text = "Policy tester initialized successfully"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize policy tester", e)
            binding.tvStatus.text = "Failed to initialize tester: ${e.message}"
            Toast.makeText(this, "Initialization failed", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun runAllTests() {
        binding.btnRunAllTests.isEnabled = false
        binding.btnRunBasicTests.isEnabled = false
        binding.btnRunStressTests.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE
        binding.tvStatus.text = "Running comprehensive policy enforcement tests..."
        
        lifecycleScope.launch {
            try {
                val startTime = System.currentTimeMillis()
                val results = policyTester.runAllTests()
                val duration = System.currentTimeMillis() - startTime
                
                displayTestResults(results, duration)
                
            } catch (e: Exception) {
                Log.e(TAG, "Test execution failed", e)
                binding.tvStatus.text = "Test execution failed: ${e.message}"
                binding.tvResults.text = "Error: ${e.message}"
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.btnRunAllTests.isEnabled = true
                binding.btnRunBasicTests.isEnabled = true
                binding.btnRunStressTests.isEnabled = true
            }
        }
    }
    
    private fun runBasicTests() {
        binding.btnRunAllTests.isEnabled = false
        binding.btnRunBasicTests.isEnabled = false
        binding.btnRunStressTests.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE
        binding.tvStatus.text = "Running basic policy tests..."
        
        lifecycleScope.launch {
            try {
                // This would be a subset of tests for basic functionality
                val startTime = System.currentTimeMillis()
                val results = policyTester.runAllTests() // Simplified - would run subset
                val duration = System.currentTimeMillis() - startTime
                
                displayTestResults(results, duration)
                
            } catch (e: Exception) {
                Log.e(TAG, "Basic test execution failed", e)
                binding.tvStatus.text = "Basic test execution failed: ${e.message}"
                binding.tvResults.text = "Error: ${e.message}"
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.btnRunAllTests.isEnabled = true
                binding.btnRunBasicTests.isEnabled = true
                binding.btnRunStressTests.isEnabled = true
            }
        }
    }
    
    private fun runStressTests() {
        binding.btnRunAllTests.isEnabled = false
        binding.btnRunBasicTests.isEnabled = false
        binding.btnRunStressTests.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE
        binding.tvStatus.text = "Running stress tests (this may take several minutes)..."
        
        lifecycleScope.launch {
            try {
                // This would focus on performance and resilience tests
                val startTime = System.currentTimeMillis()
                val results = policyTester.runAllTests() // Simplified - would run stress subset
                val duration = System.currentTimeMillis() - startTime
                
                displayTestResults(results, duration)
                
            } catch (e: Exception) {
                Log.e(TAG, "Stress test execution failed", e)
                binding.tvStatus.text = "Stress test execution failed: ${e.message}"
                binding.tvResults.text = "Error: ${e.message}"
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.btnRunAllTests.isEnabled = true
                binding.btnRunBasicTests.isEnabled = true
                binding.btnRunStressTests.isEnabled = true
            }
        }
    }
    
    private fun displayTestResults(
        results: PolicyEnforcementTester.PolicyTestResults,
        duration: Long
    ) {
        val statusColor = if (results.overallSuccess) {
            android.graphics.Color.GREEN
        } else {
            android.graphics.Color.RED
        }
        
        binding.tvStatus.text = if (results.overallSuccess) {
            "All tests passed! ✓"
        } else {
            "Some tests failed ✗"
        }
        binding.tvStatus.setTextColor(statusColor)
        
        // Format detailed results
        val detailedResults = buildString {
            appendLine("=== TEST EXECUTION SUMMARY ===")
            appendLine("Duration: ${duration}ms")
            appendLine("Overall Success: ${results.overallSuccess}")
            appendLine("Total Tests: ${results.totalTests}")
            appendLine("Passed: ${results.passedTests}")
            appendLine("Failed: ${results.failedTests}")
            appendLine("Success Rate: ${(results.passedTests.toDouble() / results.totalTests * 100).toInt()}%")
            appendLine()
            
            appendLine("=== DETAILED RESULTS ===")
            results.testResults.groupBy { it.category }.forEach { (category, tests) ->
                appendLine("--- $category ---")
                tests.forEach { test ->
                    val status = if (test.success) "✓ PASS" else "✗ FAIL"
                    appendLine("$status - ${test.testName} (${test.duration}ms)")
                    if (!test.success || test.details.isNotBlank()) {
                        appendLine("    ${test.details}")
                    }
                }
                appendLine()
            }
            
            appendLine("=== SUMMARY BY CATEGORY ===")
            PolicyEnforcementTester.TestCategory.entries.forEach { category ->
                val categoryTests = results.testResults.filter { it.category == category }
                if (categoryTests.isNotEmpty()) {
                    val categoryPassed = categoryTests.count { it.success }
                    val rate = (categoryPassed.toDouble() / categoryTests.size * 100).toInt()
                    appendLine("$category: $categoryPassed/${categoryTests.size} ($rate%)")
                }
            }
        }
        
        binding.tvResults.text = detailedResults
        
        // Show toast with quick summary
        val toastMessage = if (results.overallSuccess) {
            "All ${results.totalTests} tests passed!"
        } else {
            "${results.failedTests} of ${results.totalTests} tests failed"
        }
        Toast.makeText(this, toastMessage, Toast.LENGTH_LONG).show()
        
        Log.i(TAG, "Test results displayed: $toastMessage")
    }
    
    private fun clearTestResults() {
        binding.tvResults.text = "Test results cleared. Ready to run tests."
        binding.tvStatus.text = "Ready to run policy enforcement tests"
        binding.tvStatus.setTextColor(android.graphics.Color.BLACK)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (::policyTester.isInitialized) {
            policyTester.cleanup()
        }
    }
}
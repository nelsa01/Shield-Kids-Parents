#!/usr/bin/env python3
"""
Shield Kids Implementation Validator
Validates the code structure and identifies potential issues before building
"""

import os
import re
import json
from pathlib import Path

class ImplementationValidator:
    def __init__(self, project_root):
        self.project_root = Path(project_root)
        self.results = {"errors": [], "warnings": [], "info": [], "summary": {}}
        
    def validate(self):
        print("🔍 Validating Shield Kids Implementation...")
        print("=" * 50)
        
        self.check_project_structure()
        self.check_manifest_file()
        self.check_kotlin_files()
        self.check_resource_files()
        self.check_dependencies()
        
        self.print_results()
        return len(self.results["errors"]) == 0
        
    def check_project_structure(self):
        """Check if all required files and directories exist"""
        required_paths = [
            "app/src/main/AndroidManifest.xml",
            "app/build.gradle.kts",
            "app/src/main/java/com/shieldtechhub/shieldkids",
            "app/src/main/res",
            "gradle/wrapper/gradle-wrapper.properties"
        ]
        
        for path in required_paths:
            full_path = self.project_root / path
            if not full_path.exists():
                self.results["errors"].append(f"Missing required path: {path}")
            else:
                self.results["info"].append(f"✓ Found: {path}")
    
    def check_manifest_file(self):
        """Validate AndroidManifest.xml"""
        manifest_path = self.project_root / "app/src/main/AndroidManifest.xml"
        
        if not manifest_path.exists():
            self.results["errors"].append("AndroidManifest.xml not found")
            return
            
        try:
            content = manifest_path.read_text(encoding='utf-8')
            
            # Check for required permissions
            required_permissions = [
                "android.permission.INTERNET",
                "android.permission.FOREGROUND_SERVICE",
                "android.permission.RECEIVE_BOOT_COMPLETED",
                "android.permission.PACKAGE_USAGE_STATS",
                "android.permission.QUERY_ALL_PACKAGES"
            ]
            
            for permission in required_permissions:
                if permission in content:
                    self.results["info"].append(f"✓ Permission: {permission}")
                else:
                    self.results["warnings"].append(f"Missing permission: {permission}")
            
            # Check for required services and receivers
            required_components = [
                "ShieldMonitoringService",
                "SystemEventReceiver", 
                "BootReceiver",
                "ShieldDeviceAdminReceiver"
            ]
            
            for component in required_components:
                if component in content:
                    self.results["info"].append(f"✓ Component: {component}")
                else:
                    self.results["errors"].append(f"Missing component: {component}")
                    
        except Exception as e:
            self.results["errors"].append(f"Error reading AndroidManifest.xml: {e}")
    
    def check_kotlin_files(self):
        """Check Kotlin source files"""
        kotlin_dir = self.project_root / "app/src/main/java/com/shieldtechhub/shieldkids"
        
        if not kotlin_dir.exists():
            self.results["errors"].append("Kotlin source directory not found")
            return
            
        # Key implementation files to check
        key_files = {
            "common/utils/PermissionManager.kt": "Permission management system",
            "common/base/ShieldMonitoringService.kt": "Background monitoring service",
            "common/base/SystemEventReceiver.kt": "System event monitoring",
            "common/base/ShieldDeviceAdminReceiver.kt": "Device admin functionality",
            "common/utils/DeviceAdminManager.kt": "Device admin management",
            "features/app_management/service/AppInventoryManager.kt": "App inventory system",
            "SystemTestActivity.kt": "System testing interface"
        }
        
        for file_path, description in key_files.items():
            full_path = kotlin_dir / file_path
            if full_path.exists():
                self.results["info"].append(f"✓ {description}: {file_path}")
                self.validate_kotlin_syntax(full_path, description)
            else:
                self.results["errors"].append(f"Missing {description}: {file_path}")
    
    def validate_kotlin_syntax(self, file_path, description):
        """Basic Kotlin syntax validation"""
        try:
            content = file_path.read_text(encoding='utf-8')
            
            # Check for basic syntax issues
            if not content.strip().startswith("package "):
                self.results["warnings"].append(f"{description}: Missing package declaration")
            
            # Check for TODO comments that might indicate incomplete implementation
            todo_count = len(re.findall(r'//\s*TODO', content, re.IGNORECASE))
            if todo_count > 0:
                self.results["warnings"].append(f"{description}: {todo_count} TODO items found")
                
        except Exception as e:
            self.results["warnings"].append(f"Error validating {description}: {e}")
    
    def check_resource_files(self):
        """Check resource files"""
        res_dir = self.project_root / "app/src/main/res"
        
        required_resources = [
            "layout/activity_system_test.xml",
            "layout/activity_device_admin_setup.xml", 
            "xml/device_admin_policies.xml",
            "values/strings.xml",
            "values/colors.xml"
        ]
        
        for resource in required_resources:
            full_path = res_dir / resource
            if full_path.exists():
                self.results["info"].append(f"✓ Resource: {resource}")
            else:
                self.results["warnings"].append(f"Missing resource: {resource}")
    
    def check_dependencies(self):
        """Check build.gradle.kts dependencies"""
        build_file = self.project_root / "app/build.gradle.kts"
        
        if not build_file.exists():
            self.results["errors"].append("build.gradle.kts not found")
            return
            
        try:
            content = build_file.read_text(encoding='utf-8')
            
            required_deps = [
                "kotlinx-coroutines-android",
                "androidx.lifecycle",
                "firebase-auth",
                "androidx.appcompat"
            ]
            
            for dep in required_deps:
                if dep in content:
                    self.results["info"].append(f"✓ Dependency: {dep}")
                else:
                    self.results["warnings"].append(f"Missing dependency: {dep}")
                    
        except Exception as e:
            self.results["errors"].append(f"Error reading build.gradle.kts: {e}")
    
    def print_results(self):
        """Print validation results"""
        print("\n📊 VALIDATION RESULTS")
        print("=" * 50)
        
        if self.results["errors"]:
            print(f"\n❌ ERRORS ({len(self.results['errors'])})")
            for error in self.results["errors"]:
                print(f"   • {error}")
        
        if self.results["warnings"]:
            print(f"\n⚠️  WARNINGS ({len(self.results['warnings'])})")
            for warning in self.results["warnings"]:
                print(f"   • {warning}")
        
        if self.results["info"]:
            print(f"\n✅ SUCCESS ({len(self.results['info'])})")
            for info in self.results["info"][:10]:  # Show first 10
                print(f"   • {info}")
            if len(self.results["info"]) > 10:
                print(f"   ... and {len(self.results['info']) - 10} more")
        
        # Summary
        total_errors = len(self.results["errors"])
        total_warnings = len(self.results["warnings"])
        total_success = len(self.results["info"])
        
        print(f"\n📈 SUMMARY")
        print("=" * 20)
        print(f"✅ Success: {total_success}")
        print(f"⚠️  Warnings: {total_warnings}")
        print(f"❌ Errors: {total_errors}")
        
        if total_errors == 0:
            print(f"\n🎉 IMPLEMENTATION READY FOR TESTING!")
            print("   Run the build script to compile and test")
        else:
            print(f"\n🔧 FIX ERRORS BEFORE TESTING")
            print("   Address the errors above, then re-validate")

if __name__ == "__main__":
    validator = ImplementationValidator(".")
    success = validator.validate()
    exit(0 if success else 1)
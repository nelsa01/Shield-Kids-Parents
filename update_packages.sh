#!/bin/bash

# Script to update package declarations for moved files

echo "Updating package declarations..."

# Authentication files
find app/src/main/java/com/shieldtechhub/shieldkids/features/authentication -name "*.kt" -exec sed -i 's/^package com\.shieldtechhub\.shieldkids$/package com.shieldtechhub.shieldkids.features.authentication/' {} \;

# Child management files  
find app/src/main/java/com/shieldtechhub/shieldkids/features/child_management -name "*.kt" -exec sed -i 's/^package com\.shieldtechhub\.shieldkids$/package com.shieldtechhub.shieldkids.features.child_management.ui/' {} \;

# Onboarding files
find app/src/main/java/com/shieldtechhub/shieldkids/features/onboarding -name "*.kt" -exec sed -i 's/^package com\.shieldtechhub\.shieldkids$/package com.shieldtechhub.shieldkids.features.onboarding.ui/' {} \;

# Device setup files
find app/src/main/java/com/shieldtechhub/shieldkids/features/device_setup -name "*.kt" -exec sed -i 's/^package com\.shieldtechhub\.shieldkids$/package com.shieldtechhub.shieldkids.features.device_setup.ui/' {} \;

# App management files
find app/src/main/java/com/shieldtechhub/shieldkids/features/app_management/ui -name "*.kt" -exec sed -i 's/^package com\.shieldtechhub\.shieldkids$/package com.shieldtechhub.shieldkids.features.app_management.ui/' {} \;

# Policy UI files
find app/src/main/java/com/shieldtechhub/shieldkids/features/policy/ui -name "*.kt" -exec sed -i 's/^package com\.shieldtechhub\.shieldkids$/package com.shieldtechhub.shieldkids.features.policy.ui/' {} \;

# Core files
find app/src/main/java/com/shieldtechhub/shieldkids/features/core -name "*.kt" -exec sed -i 's/^package com\.shieldtechhub\.shieldkids$/package com.shieldtechhub.shieldkids.features.core/' {} \;

echo "Package declarations updated!"
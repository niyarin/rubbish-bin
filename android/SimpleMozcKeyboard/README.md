# SimpleMozcKeyboard

<img src="https://github.com/user-attachments/assets/20274182-1f7a-4b85-ac0d-75d748419857" width="200" />

## Build

Put local.properties file.
```bash
cat > local.properties <<'EOF'
sdk.dir=/path/to/Android/Sdk
EOF
```

Download `mozc-wrapper.aar`.

```bash
mkdir -p app/libs
wget -O app/libs/mozc-wrapper.aar \
  https://github.com/niyarin/mozc-wrapper-for-android/releases/download/v0.0.1/mozc-wrapper-0.0.1.aar
```

Generating mozc.data
```bash
git clone https://github.com/google/mozc
cd mozc
git checkout 3.33.6133
cd src
bazel build --config=oss_linux //data_manager/oss:mozc_dataset_for_oss

# copy to app/src/main/assets/mozc.data
cp bazel-bin/data_manager/oss/mozc.data ../../app/src/main/assets/mozc.data
```

Build.

```bash
./gradlew :app:assembleDebug
```


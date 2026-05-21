# Gyeongju Heritage Classifier Android Assets

Place these files under `app/src/main/assets/` in the Android project.

Files:
- `gyeongju_heritage_classifier.onnx`: ONNX image classification model.
- `class_labels.json`: class index to slug/Korean label mapping.
- `preprocessing.json`: image preprocessing contract used during training/export.

Inference contract:
1. Convert input image to RGB.
2. Resize/crop to 224 x 224.
3. Convert pixels to float in range 0.0 to 1.0.
4. Normalize each channel with mean `[0.485, 0.456, 0.406]` and std `[0.229, 0.224, 0.225]`.
5. Feed tensor as NCHW shape `[1, 3, 224, 224]` to input name `input`.
6. Read output name `probabilities` and choose `argmax` as the predicted class index.
7. Map the index through `class_labels.json`.

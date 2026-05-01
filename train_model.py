import os
from ultralytics import YOLO

def main():
    dataset_path = "8ballpool Detection.v1-8ballpool-detection.yolov8/data.yaml"
    
    # Check if dataset exists
    if not os.path.exists(dataset_path):
        print(f"Error: Could not find {dataset_path}")
        print("Please make sure the '8ballpool Detection.v1-8ballpool-detection.yolov8' folder is inside D:\\cheto")
        return

    print("=========================================")
    print("🚀 Starting YOLOv8 Training for Android...")
    print("=========================================")

    # 1. Load the YOLOv8 Nano model (Nano is best for Mobile/Android speed)
    model = YOLO("yolov8n.pt")

    # 2. Train the model
    print("\n[1/2] Training Model (This will take some time depending on your PC)...")
    model.train(
        data=dataset_path,
        epochs=50,          # 50 rounds of training (you can increase this for better accuracy)
        imgsz=640,          # Standard image size
        batch=16,           # Batch size
        device="cpu",       # Change "cpu" to 0 if you have an NVIDIA Graphics Card for 10x faster training
        project="cheto_model",
        name="android_export"
    )

    # 3. Export to TFLite (Android format)
    print("\n[2/2] Exporting Model to TFLite for Android...")
    
    # We export to tflite and enable int8 quantization to make it fast on Android
    export_path = model.export(
        format="tflite",
        int8=True,       # Makes the model much lighter and faster for mobile
        imgsz=640
    )

    print("\n✅ All Done!")
    print(f"Your Android model is ready at: {export_path}")
    print("Copy the 'best_saved_model/best_full_integer_quant.tflite' file to your app/src/main/assets folder.")

if __name__ == '__main__':
    main()

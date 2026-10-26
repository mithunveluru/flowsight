import os

# Read by OpenCV when it first loads: a small, highly compressed upload must not decode to gigabytes.
os.environ.setdefault("OPENCV_IO_MAX_IMAGE_PIXELS", "50000000")

# Eval image: the backend's Java/Maven toolchain plus Tesseract, Python and fonts.
# The base is pinned by digest and apt resolves against a fixed Ubuntu archive snapshot,
# so the generated images and OCR output are byte-identical on every machine.
FROM maven:3.9-eclipse-temurin-17@sha256:1a352420f7aba21f5ad08df31bab55f74c013fb491f1ae8ab1dd7ff9ed698584

ARG UBUNTU_SNAPSHOT=20260901T000000Z

RUN apt-get update --snapshot "${UBUNTU_SNAPSHOT}" \
 && apt-get install -y --no-install-recommends \
      tesseract-ocr=5.3.4-1build5 \
      tesseract-ocr-eng=1:4.1.0-2 \
      python3=3.12.3-0ubuntu2.1 \
      python3-pil=10.2.0-1ubuntu1.3 \
      python3-numpy=1:1.26.4+ds-6ubuntu1 \
      python3-pytest=7.4.4-1 \
      fonts-dejavu-core=2.37-8 \
      fonts-dejavu-mono=2.37-8 \
      fonts-liberation=1:2.1.5-3 \
      fonts-freefont-ttf=20211204+svn4273-2 \
 && rm -rf /var/lib/apt/lists/*

# One Tesseract thread per process; the runner parallelises across receipts instead.
ENV OMP_THREAD_LIMIT=1 \
    PYTHONDONTWRITEBYTECODE=1

# The maven image's entrypoint copies settings into /root, which fails for a non-root uid.
ENTRYPOINT []

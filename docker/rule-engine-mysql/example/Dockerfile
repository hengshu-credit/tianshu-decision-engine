FROM python:3.12-slim-bookworm
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY supplier.py credentials.py ./
CMD ["python", "-u", "supplier.py"]

FROM python:3.10
WORKDIR /goodloop.google/src/python/chatroundabout
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
CMD ["python", "main.py"]
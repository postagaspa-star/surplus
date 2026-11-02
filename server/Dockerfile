# Server di Surplus: solo FastAPI, niente di compilato.
FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1

WORKDIR /srv

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app

# I dati e i pacchetti arrivano da fuori (volumi del NAS).
ENV SURPLUS_DATI=/dati SURPLUS_PACCHETTI=/pacchetti

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--log-level", "info"]

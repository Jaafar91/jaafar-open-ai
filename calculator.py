from fastapi import FastAPI

app = FastAPI()

@app.get("/add")
def add(x: float, y: float):
    return {"result": x + y}

@app.get("/subtract")
def subtract(x: float, y: float):
    return {"result": x - y}

@app.get("/multiply")
def multiply(x: float, y: float):
    return {"result": x * y}

@app.get("/divide")

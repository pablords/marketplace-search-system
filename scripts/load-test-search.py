import asyncio
import httpx
import random
import time
import sys

# Configurações do Teste
BASE_URL = "http://api.lab.com.br/api/v1/search/products"
CONCURRENT_USERS = 20
TOTAL_REQUESTS = 1000000

SEARCH_TERMS = [
    "smartphone", "laptop", "monitor", "headphone", "teclado", 
    "mouse", "cadeira gamer", "mesa", "caixa de som", "carregador",
    "iphone", "samsung", "dell", "lg", "logitech", "razer", "camisa"
]

CATEGORIES = ["electronics", "office", "computing", "accessories"]

async def make_search(client, user_id):
    term = random.choice(SEARCH_TERMS)
    category = random.choice(CATEGORIES) if random.random() > 0.5 else None
    
    params = {
        "query": term,
        "limit": 20,
        "offset": 0
    }
    if category:
        params["category"] = category

    try:
        start_time = time.perf_counter()
        response = await client.get(BASE_URL, params=params, timeout=10.0)
        end_time = time.perf_counter()
        
        duration = (end_time - start_time) * 1000
        if response.status_code == 200:
            print(f"User {user_id}: Search for '{term}' - OK ({duration:.2f}ms)")
        else:
            print(f"User {user_id}: Search for '{term}' - ERROR {response.status_code}")
            
    except Exception as e:
        print(f"User {user_id}: Search for '{term}' - FAILED: {str(e)}")

async def run_load_test():
    print(f"Starting load test with {CONCURRENT_USERS} concurrent users...")
    print(f"Target URL: {BASE_URL}")
    
    async with httpx.AsyncClient() as client:
        tasks = []
        for i in range(TOTAL_REQUESTS):
            # Limita a concorrência
            if len(tasks) >= CONCURRENT_USERS:
                done, pending = await asyncio.wait(tasks, return_when=asyncio.FIRST_COMPLETED)
                tasks = list(pending)
            
            tasks.append(asyncio.create_task(make_search(client, i)))
            
        await asyncio.gather(*tasks)

if __name__ == "__main__":
    try:
        asyncio.run(run_load_test())
        print("\nLoad test completed!")
    except KeyboardInterrupt:
        print("\nTest interrupted by user.")

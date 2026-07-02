import asyncio
import httpx
import random
import time
import sys

# Configurações do Teste
BASE_URL = "http://api.lab.com.br/api/v1/search/products"

SEARCH_TERMS = [
    "smartphone", "laptop", "monitor", "headphone", "teclado", 
    "mouse", "cadeira gamer", "mesa", "caixa de som", "carregador",
    "iphone", "samsung", "dell", "lg", "logitech", "razer", "camisa"
]

CATEGORIES = ["electronics", "office", "computing", "accessories"]

# Estágios do Ramp-up: (número de usuários concorrentes, duração do estágio em segundos)
RAMP_UP_STAGES = [
    (10, 20),   # 10 usuários simultâneos por 20 segundos
    (30, 20),   # 30 usuários simultâneos por 20 segundos
    (60, 30),   # 60 usuários simultâneos por 30 segundos
    (100, 30),  # 100 usuários simultâneos por 30 segundos
    (150, 45),  # 150 usuários simultâneos por 45 segundos (pico)
]

# Objeto global de estatísticas
stats = {
    "success": 0,
    "error": 0,
    "failed": 0,
    "durations": []
}

async def make_search(client, worker_id, request_id):
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
        response = await client.get(BASE_URL, params=params, timeout=5.0)
        end_time = time.perf_counter()
        
        duration = (end_time - start_time) * 1000
        stats["durations"].append(duration)
        
        if response.status_code == 200:
            stats["success"] += 1
            print(f"Worker {worker_id}-{request_id}: Search for '{term}' - OK ({duration:.2f}ms)")
        else:
            stats["error"] += 1
            print(f"Worker {worker_id}-{request_id}: Search for '{term}' - ERROR {response.status_code} ({duration:.2f}ms)")
            
    except Exception as e:
        stats["failed"] += 1
        print(f"Worker {worker_id}-{request_id}: Search for '{term}' - FAILED: {str(e)}")

# Loop de execução de cada usuário virtual
async def virtual_user(client, worker_id, stop_event):
    request_id = 0
    while not stop_event.is_set():
        await make_search(client, worker_id, request_id)
        request_id += 1
        # Simula think-time humano aleatório entre requisições
        await asyncio.sleep(random.uniform(0.1, 0.5))

async def run_load_test():
    print("🚀 Starting search load test with Ramp-up stages...")
    print(f"Target URL: {BASE_URL}")
    print(f"Ramp-up Stages (concurrency, duration): {RAMP_UP_STAGES}")
    
    stop_event = asyncio.Event()
    active_workers = {}
    
    async with httpx.AsyncClient() as client:
        start_test_time = time.time()
        
        for stage_idx, (target_concurrency, duration) in enumerate(RAMP_UP_STAGES):
            current_concurrency = len(active_workers)
            print(f"\n📈 --- Entering Stage {stage_idx + 1}/{len(RAMP_UP_STAGES)}: Target Concurrency = {target_concurrency} for {duration}s ---")
            
            # Subir mais usuários se necessário
            if target_concurrency > current_concurrency:
                to_create = target_concurrency - current_concurrency
                print(f"➕ Spawning {to_create} new virtual users...")
                for w_id in range(current_concurrency, target_concurrency):
                    active_workers[w_id] = asyncio.create_task(virtual_user(client, w_id, stop_event))
            
            # Remover usuários se necessário (incomum no ramp-up, mas suportado)
            elif target_concurrency < current_concurrency:
                to_remove = current_concurrency - target_concurrency
                print(f"➖ Stopping {to_remove} virtual users...")
                for w_id in range(target_concurrency, current_concurrency):
                    task = active_workers.pop(w_id)
                    task.cancel()
            
            # Esperar a duração do estágio corrente
            await asyncio.sleep(duration)
            
            # Mostrar resumo parcial do estágio
            total_reqs = stats["success"] + stats["error"] + stats["failed"]
            avg_dur = sum(stats["durations"]) / len(stats["durations"]) if stats["durations"] else 0
            print(f"📊 Stage stats: Total Requests = {total_reqs} | Success = {stats['success']} | Avg Latency = {avg_dur:.2f}ms")

        # Fim do teste: Sinalizar parada
        print("\n🛑 Stopping all virtual users...")
        stop_event.set()
        
        # Cancelar qualquer worker que ainda esteja ativo
        for task in active_workers.values():
            task.cancel()
            
        await asyncio.gather(*active_workers.values(), return_exceptions=True)
        
        total_time = time.time() - start_test_time
        total_reqs = stats["success"] + stats["error"] + stats["failed"]
        avg_dur = sum(stats["durations"]) / len(stats["durations"]) if stats["durations"] else 0
        
        print("\n=============================================")
        print("🏁 Load Test Summary")
        print("=============================================")
        print(f"⏱️  Total Duration: {total_time:.2f} seconds")
        print(f"📬 Total Requests Made: {total_reqs}")
        print(f"✅ Success Rate: {stats['success']} ({stats['success'] / (total_reqs or 1) * 100:.2f}%)")
        print(f"❌ Error HTTP 5xx/4xx Rate: {stats['error']} ({stats['error'] / (total_reqs or 1) * 100:.2f}%)")
        print(f"⚠️  Failed/Exception Rate: {stats['failed']} ({stats['failed'] / (total_reqs or 1) * 100:.2f}%)")
        print(f"🚀 Average Latency: {avg_dur:.2f} ms")
        if stats["durations"]:
            sorted_durs = sorted(stats["durations"])
            p95 = sorted_durs[int(len(sorted_durs) * 0.95)]
            p99 = sorted_durs[int(len(sorted_durs) * 0.99)]
            print(f"📊 Percentile 95%: {p95:.2f} ms")
            print(f"📊 Percentile 99%: {p99:.2f} ms")
        print("=============================================")

if __name__ == "__main__":
    try:
        asyncio.run(run_load_test())
    except KeyboardInterrupt:
        print("\nTest interrupted by user.")

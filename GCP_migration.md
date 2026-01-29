# Plan Migracji Weather Service do Google Kubernetes Engine (GKE)

## Cel
Przenieść aplikację weather-service z GitHub Container Registry (GHCR) do Google Cloud Platform (GKE), używając Google Artifact Registry do przechowywania obrazów Docker. Plan skupia się na **edukacji** - każdy krok wyjaśnia DLACZEGO i JAK działa Kubernetes i GCP.

## Kontekst Obecnej Konfiguracji

**Obecna struktura:**
- **Aplikacja**: Spring Boot 3.5.5 (Kotlin), deployowana jako WAR
- **Baza danych**: PostgreSQL 16-alpine w Kubernetes
- **Registry**: ghcr.io/polikkox/weather-service:latest
- **Problemy do rozwiązania**:
    1. hostPath storage (nie działa w GKE)
    2. emptyDir dla PostgreSQL (dane ulotne)
    3. ghcr-secret (nie potrzebny w GKE z Artifact Registry)
    4. host.docker.internal w WEATHER_API_BASE_URL (nie działa w K8s)
    5. Security: uruchamianie jako root
    6. Brak tagów wersji (tylko :latest)

**Status GCP**: Nowe konto z $300 trial - **wszystko będzie darmowe przez 90 dni!**

---

## SEKCJA EDUKACYJNA: Kluczowe Koncepty

### 1. Google Artifact Registry vs GHCR

**Google Artifact Registry** to następca GCR (Google Container Registry), który przechowuje obrazy Docker.

**Dlaczego migrujemy z GHCR do Artifact Registry?**
- ✅ **Integracja z GKE**: Automatyczny dostęp, nie trzeba konfigurować image pull secrets
- ✅ **Szybsze pobieranie**: Obrazy w tej samej infrastrukturze co klaster GKE
- ✅ **Bezpieczeństwo**: Automatyczna integracja z Google Cloud IAM
- ✅ **Vulnerability scanning**: Automatyczne skanowanie obrazów pod kątem luk bezpieczeństwa

**Format URL obrazu:**
```
us-central1-docker.pkg.dev/PROJECT-ID/REPOSITORY/IMAGE:TAG
                         └─ weather-app-prod/weather-repo/weather-service:1.0.0
```

### 2. Persistent Storage w GKE

**Problem**: Obecna konfiguracja używa `hostPath` (dla weather-service) i `emptyDir` (dla PostgreSQL) - oba nie są odpowiednie dla produkcji.

**Jak działa storage w Kubernetes:**

```
StorageClass (jak tworzyć dyski)
    ↓ automatyczne provisionowanie
PersistentVolume (fizyczny dysk w GCP)
    ↓ binding
PersistentVolumeClaim (rezerwacja dysku przez pod)
    ↓ montowanie
Pod (używa PVC jako volume)
```

**W GKE:**
- **standard-rwo**: HDD persistent disk (tańszy, ~$0.04/GB/miesiąc)
- **premium-rwo**: SSD persistent disk (szybszy, ~$0.17/GB/miesiąc)
- **Dla learning**: standard-rwo wystarcza

### 3. Networking w Kubernetes

**Problem**: `WEATHER_API_BASE_URL: "http://host.docker.internal:8081"` nie działa w K8s.

**Rozwiązanie**: Service Discovery w Kubernetes
```
postgres-service:5432              → w tym samym namespace
postgres-service.default:5432      → pełna nazwa (namespace: default)
weather-api-service:8081           → inny mikroservice w klastrze
```

**LoadBalancer w GKE:**
- Automatycznie tworzy Google Cloud Load Balancer
- Przydziela publiczny External IP
- Dostęp z internetu: http://EXTERNAL_IP/
- Koszt: ~$18/miesiąc (ale masz $300 trial, więc darmowe przez 90 dni!)

### 4. Security Best Practices

**Obecny problem**: SecurityContext z `runAsUser: 0` (root)

**Dockerfile** już definiuje non-root user `spring:spring`, ale deployment.yaml go nadpisuje.

**Poprawka**: Usuń securityContext z deployment.yaml lub ustaw na non-root (UID 1000).

---

## ETAP 1: Przygotowanie Środowiska GCP

### Krok 1.1: Instalacja Google Cloud SDK

**Na Windows:**
1. Pobierz installer: https://cloud.google.com/sdk/docs/install
2. Uruchom installer i zainstaluj w domyślnej lokalizacji
3. Po instalacji, otwórz nowy terminal PowerShell

**Weryfikacja:**
```powershell
gcloud version
# Oczekiwany output: Google Cloud SDK 450.x.x
```

### Krok 1.2: Zaloguj się do GCP i utwórz projekt

```bash
# 1. Zaloguj się (otwiera przeglądarkę)
gcloud auth login

# 2. Utwórz nowy projekt
gcloud projects create weather-app-prod --name="Weather Service Production"

# 3. Ustaw jako domyślny projekt
gcloud config set project weather-app-prod

# 4. Włącz wymagane API (WAŻNE: każde API wymaga aktywacji!)
gcloud services enable container.googleapis.com          # GKE
gcloud services enable artifactregistry.googleapis.com   # Artifact Registry
gcloud services enable compute.googleapis.com            # Compute Engine (dla nodes)

# 5. Ustaw domyślny region i zone
gcloud config set compute/region us-central1
gcloud config set compute/zone us-central1-a

# 6. Weryfikacja
gcloud config list
```

**Dlaczego us-central1?**
- ✅ Free tier eligible
- ✅ Stabilny region (jeden z najstarszych Google)
- ✅ Dobre opóźnienia dla Europy i USA

### Krok 1.3: Utwórz Artifact Registry Repository

```bash
# Utwórz repository dla obrazów Docker
gcloud artifacts repositories create weather-repo \
  --repository-format=docker \
  --location=us-central1 \
  --description="Weather Service Docker Images"

# Weryfikacja
gcloud artifacts repositories list

# Konfiguracja Docker do używania Artifact Registry
gcloud auth configure-docker us-central1-docker.pkg.dev
```

**Co to robi:**
- Tworzy prywatne repozytorium dla Twoich obrazów Docker
- `us-central1` = region (musi być ten sam co klaster GKE dla szybkości)
- `weather-repo` = nazwa repozytorium

### Krok 1.4: Utwórz klaster GKE

```bash
# Utwórz najmniejszy klaster (1 node, e2-small)
gcloud container clusters create weather-cluster \
  --zone=us-central1-a \
  --machine-type=e2-small \
  --num-nodes=1 \
  --disk-size=10 \
  --disk-type=pd-standard \
  --enable-autorepair \
  --enable-autoupgrade \
  --scopes=gke-default

# Czas utworzenia: ~5-10 minut
# Kawa time! ☕
```

**Parametry wyjaśnione:**
- `--zone=us-central1-a`: Jeden availability zone (zonal cluster)
- `--machine-type=e2-small`: 2 vCPU, 2GB RAM (~$13/miesiąc, ale masz trial!)
- `--num-nodes=1`: Jeden node (wystarczy dla learning)
- `--disk-size=10`: 10GB boot disk dla node'a
- `--enable-autorepair`: Automatyczna naprawa broken nodes
- `--enable-autoupgrade`: Automatyczne updaty Kubernetes

**Koszt (po wyczerpaniu $300 trial):**
- e2-small node: ~$13/miesiąc
- Persistent Disk: $0 (w ramach 30GB free tier)
- **Przez 90 dni z $300 trial: DARMOWE!**

### Krok 1.5: Konfiguracja kubectl

```bash
# Pobierz credentials dla kubectl
gcloud container clusters get-credentials weather-cluster --zone=us-central1-a

# Weryfikacja połączenia
kubectl cluster-info
kubectl get nodes

# Oczekiwany output:
# NAME                              STATUS   ROLES    AGE   VERSION
# gke-weather-cluster-default-pool  Ready    <none>   5m    v1.28.x
```

**Co to robi:**
- Pobiera certyfikaty i konfigurację klastra
- Dodaje kontekst do `~/.kube/config`
- Od teraz wszystkie komendy `kubectl` działają na klastrze GKE

---

## ETAP 2: Build i Push Obrazu do Artifact Registry

### Krok 2.1: Build obrazu lokalnie

```bash
# Przejdź do katalogu projektu
cd C:\Users\damian.targosz\intelij-workspace\weatherapp\wheather-service

# Build obrazu z tagiem dla Artifact Registry
docker build -t us-central1-docker.pkg.dev/weather-app-prod/weather-repo/weather-service:1.0.0 .

# WAŻNE: Używamy semantic versioning (1.0.0) zamiast :latest!
```

**Co się dzieje podczas build:**
1. **Stage 1 (Builder)**: Maven kompiluje kod Kotlin → WAR file
2. **Stage 2 (Runtime)**: Kopiuje WAR do lekkiego obrazu JRE
3. **Efekt**: Mały obraz (~200MB) bez Maven i source code

**Dlaczego 1.0.0 zamiast :latest?**
- ✅ Wiesz dokładnie, która wersja jest deployed
- ✅ Łatwy rollback do poprzedniej wersji
- ✅ Reproducible builds

### Krok 2.2: Push obrazu do Artifact Registry

```bash
# Push obrazu
docker push us-central1-docker.pkg.dev/weather-app-prod/weather-repo/weather-service:1.0.0

# Weryfikacja w Artifact Registry
gcloud artifacts docker images list us-central1-docker.pkg.dev/weather-app-prod/weather-repo

# Lub w konsoli web:
# https://console.cloud.google.com/artifacts/docker/weather-app-prod/us-central1/weather-repo
```

**Co się dzieje:**
1. Docker kompresuje layers obrazu
2. Sprawdza, które layers już istnieją (deduplikacja)
3. Uploaduje tylko nowe layers
4. Artifact Registry automatycznie skanuje obraz pod kątem luk bezpieczeństwa

### Krok 2.3: Vulnerability Scan (opcjonalnie)

```bash
# Sprawdź wyniki skanowania bezpieczeństwa
gcloud artifacts docker images describe \
  us-central1-docker.pkg.dev/weather-app-prod/weather-repo/weather-service:1.0.0 \
  --show-package-vulnerability

# Pokazuje znalezione CVE (luki bezpieczeństwa)
```

---

## ETAP 3: Modyfikacja Plików Kubernetes dla GKE

### Pliki do Utworzenia

#### 3.1 NOWY: `k8s/weather-service/weather-pvc.yaml`

```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: weather-data-pvc
  labels:
    app: weather-service
spec:
  accessModes:
    - ReadWriteOnce  # RWO: tylko jeden pod może montować
  storageClassName: standard-rwo  # Domyślny StorageClass w GKE
  resources:
    requests:
      storage: 1Gi  # 1GB wystarczy dla plików pogody
```

**UWAGA o ReadWriteOnce:**
- ReadWriteOnce = tylko jeden pod może montować dysk jednocześnie
- Dlatego musimy zmniejszyć `replicas: 2` → `replicas: 1` w deployment.yaml
- ReadWriteMany (wiele podów) wymaga Filestore (1TB minimum = $200/m - za drogie!)

**Dlaczego standard-rwo?**
- `standard-rwo`: HDD persistent disk (tańszy, wystarczy dla learning)
- `premium-rwo`: SSD persistent disk (szybszy, ale droższy)

### Pliki do Modyfikacji

#### 3.2 MODYFIKACJA: `k8s/weather-service/deployment.yaml`

**Zmiany do wprowadzenia:**

```yaml
# ZMIANA 1: Image URL (linia 24)
# PRZED:
image: ghcr.io/polikkox/weather-service:latest

# PO:
image: us-central1-docker.pkg.dev/weather-app-prod/weather-repo/weather-service:1.0.0

# ---

# ZMIANA 2: Usuń imagePullSecrets (linie 20-21)
# PRZED:
imagePullSecrets:
  - name: ghcr-secret

# PO:
# (usuń całą sekcję - nie jest potrzebna dla Artifact Registry w GKE)

# ---

# ZMIANA 3: Zmień replicas (linia 9)
# PRZED:
replicas: 2

# PO:
replicas: 1  # Bo używamy ReadWriteOnce storage

# ---

# ZMIANA 4: Usuń/Zmień securityContext (linie 18-19, 25-27)
# PRZED:
securityContext:
  fsGroup: 0
# ...
securityContext:
  runAsUser: 0
  runAsGroup: 0

# PO:
securityContext:
  fsGroup: 1000
# ...
securityContext:
  runAsNonRoot: true
  runAsUser: 1000
  runAsGroup: 1000

# ---

# ZMIANA 5: Volume z hostPath na PVC (linie 95-98)
# PRZED:
volumes:
  - name: weather-data
    hostPath:
      path: /mnt/data/weather-app-data
      type: DirectoryOrCreate

# PO:
volumes:
  - name: weather-data
    persistentVolumeClaim:
      claimName: weather-data-pvc

# ---

# ZMIANA 6: ImagePullPolicy (linia 28)
# PRZED:
imagePullPolicy: Always

# PO:
imagePullPolicy: IfNotPresent  # Szybsze deploy'e

# ---

# ZMIANA 7: Zmniejsz resource limits (opcjonalnie - dla oszczędności)
# PRZED:
resources:
  requests:
    memory: "512Mi"
    cpu: "250m"
  limits:
    memory: "1Gi"
    cpu: "500m"

# PO (opcjonalnie):
resources:
  requests:
    memory: "384Mi"
    cpu: "200m"
  limits:
    memory: "768Mi"
    cpu: "400m"
```

#### 3.3 MODYFIKACJA: `k8s/postgres-configmap.yaml`

```yaml
# ZMIANA: WEATHER_API_BASE_URL (linia 8)
# PRZED:
WEATHER_API_BASE_URL: "http://host.docker.internal:8081"

# PO (zastąp nazwą prawdziwego service):
WEATHER_API_BASE_URL: "http://weather-api-service:8081"

# LUB jeśli nie masz jeszcze tego serwisu (placeholder):
WEATHER_API_BASE_URL: "http://weather-api-service:8081"
```

**Wyjaśnienie:**
- Użytkownik powiedział, że WEATHER_API_BASE_URL to inny serwis w K8s
- Service Discovery w K8s: `http://SERVICE_NAME:PORT`
- Zastąp `weather-api-service` nazwą prawdziwego serwisu, gdy go stworzysz

#### 3.4 MODYFIKACJA: `k8s/postgres/postgres-deployment.yaml`

```yaml
# ZMIANA: Volume z emptyDir na PVC (linie 60-62)
# PRZED:
volumes:
  - name: postgres-storage
    emptyDir: {}

# PO:
volumes:
  - name: postgres-storage
    persistentVolumeClaim:
      claimName: postgres-pvc
```

**WAŻNE:** Plik `k8s/postgres/postgres-pvc.yaml` już istnieje i jest poprawny! Tylko zmień volume w deployment.

#### 3.5 BEZPIECZEŃSTWO: `k8s/postgres/postgres-secret.yaml`

**NIE COMMITUJ tego pliku do Git!**

**Krok 1: Usuń z Git tracking**
```bash
git rm --cached k8s/postgres/postgres-secret.yaml
echo "k8s/*-secret.yaml" >> .gitignore
```

**Krok 2: Utwórz secret bezpośrednio w klastrze (ZAMIAST przez YAML)**
```bash
kubectl create secret generic postgres-secret \
  --from-literal=POSTGRES_DB=weather \
  --from-literal=POSTGRES_USER=postgres \
  --from-literal=POSTGRES_PASSWORD=StrongPassword123!

# WAŻNE: Zmień "StrongPassword123!" na swoje hasło!
```

**Dlaczego nie przez YAML?**
- ❌ YAML w Git = hasło w plain text = SECURITY RISK
- ✅ kubectl create secret = hasło tylko w klastrze = BEZPIECZNE

---

## ETAP 4: Deployment do GKE

### Krok 4.1: Utwórz Secret

```bash
# Utwórz postgres-secret
kubectl create secret generic postgres-secret \
  --from-literal=POSTGRES_DB=weather \
  --from-literal=POSTGRES_USER=postgres \
  --from-literal=POSTGRES_PASSWORD=StrongPassword123!

# Weryfikacja (bez pokazywania wartości)
kubectl get secret postgres-secret
kubectl describe secret postgres-secret
```

### Krok 4.2: Deploy ConfigMap

```bash
kubectl apply -f k8s/postgres-configmap.yaml

# Weryfikacja
kubectl get configmap app-config
kubectl describe configmap app-config
```

### Krok 4.3: Deploy PostgreSQL

**Kolejność jest ważna:**
1. PVC (PersistentVolumeClaim)
2. Deployment
3. Service

```bash
# 1. Utwórz PVC dla PostgreSQL
kubectl apply -f k8s/postgres/postgres-pvc.yaml

# 2. Deploy PostgreSQL
kubectl apply -f k8s/postgres/postgres-deployment.yaml

# 3. Utwórz Service
kubectl apply -f k8s/postgres/postgres-service.yaml

# Monitoruj status
kubectl get pods -l app=postgres -w

# Sprawdź logi
kubectl logs -f deployment/postgres

# Czekaj aż STATUS = Running (może zająć 1-2 minuty)
```

**Co się dzieje:**
1. PVC żąda dysku → GKE tworzy GCE Persistent Disk (5GB)
2. Deployment tworzy pod → Kubernetes scheduluje na node
3. Pod montuje PVC → Dane PostgreSQL są na persistent disk
4. Service tworzy ClusterIP → Inne pody mogą łączyć się przez `postgres-service:5432`

### Krok 4.4: Deploy Weather Service

```bash
# 1. Utwórz PVC dla weather-service
kubectl apply -f k8s/weather-service/weather-pvc.yaml

# 2. Deploy aplikacji
kubectl apply -f k8s/weather-service/deployment.yaml

# 3. Utwórz LoadBalancer Service
kubectl apply -f k8s/weather-service/service.yaml

# Monitoruj status
kubectl get pods -l app=weather-service -w

# Sprawdź logi
kubectl logs -f deployment/weather-service
```

### Krok 4.5: Pobierz External IP LoadBalancera

```bash
# Sprawdź External IP
kubectl get svc weather-service-service

# Jeśli External IP jest <pending>, poczekaj 2-3 minuty
kubectl get svc weather-service-service -w

# Zapisz External IP do zmiennej
$WEATHER_SERVICE_IP = (kubectl get svc weather-service-service -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
echo $WEATHER_SERVICE_IP
```

**Co się dzieje:**
- GKE tworzy Google Cloud Load Balancer
- Przydziela publiczny External IP
- Konfiguruje health checks
- **Czas: 2-5 minut**

---

## ETAP 5: Weryfikacja i Testowanie

### Test 1: Health Check

```bash
# Sprawdź health endpoint
curl http://$WEATHER_SERVICE_IP/actuator/health

# Oczekiwany output:
# {"status":"UP"}
```

### Test 2: API Endpoint

```bash
# Test API endpoint
curl "http://$WEATHER_SERVICE_IP/api/v1/check-weather?city=Warsaw"

# PowerShell:
Invoke-WebRequest -Uri "http://$WEATHER_SERVICE_IP/api/v1/check-weather?city=Warsaw"
```

### Test 3: Swagger UI

```bash
# Otwórz w przeglądarce
start "http://$WEATHER_SERVICE_IP/swagger-ui.html"  # Windows
open "http://$WEATHER_SERVICE_IP/swagger-ui.html"   # macOS
```

### Test 4: Persistent Storage (WAŻNY TEST!)

**Sprawdźmy, czy dane przetrwają restart poda:**

```bash
# 1. Dodaj testowe dane do PostgreSQL
kubectl exec -it deployment/postgres -- psql -U postgres -d weather -c \
  "CREATE TABLE IF NOT EXISTS test_data (id SERIAL, message TEXT); \
   INSERT INTO test_data (message) VALUES ('Test persistence!');"

# 2. Sprawdź dane
kubectl exec -it deployment/postgres -- psql -U postgres -d weather -c \
  "SELECT * FROM test_data;"

# 3. Usuń pod (Kubernetes automatycznie utworzy nowy)
kubectl delete pod -l app=postgres

# 4. Poczekaj na restart (30-60s)
kubectl get pods -l app=postgres -w

# 5. Sprawdź, czy dane NADAL ISTNIEJĄ
kubectl exec -it deployment/postgres -- psql -U postgres -d weather -c \
  "SELECT * FROM test_data;"

# ✅ Jeśli widzisz "Test persistence!" - SUKCES! Dane są persistent!
```

### Test 5: Logi i Monitoring

```bash
# Logi weather-service
kubectl logs -f deployment/weather-service --tail=50

# Logi PostgreSQL
kubectl logs -f deployment/postgres --tail=50

# Wszystkie resources
kubectl get all

# Events (jeśli są problemy)
kubectl get events --sort-by='.lastTimestamp'
```

---

## ETAP 6: Troubleshooting (Jeśli Coś Nie Działa)

### Problem 1: Image Pull Error

```
Error: ErrImagePull
Message: Failed to pull image
```

**Rozwiązanie:**
```bash
# Sprawdź, czy obraz istnieje
gcloud artifacts docker images list \
  us-central1-docker.pkg.dev/weather-app-prod/weather-repo/weather-service

# Sprawdź permissions
gcloud projects get-iam-policy weather-app-prod
```

### Problem 2: CrashLoopBackOff

```
State: CrashLoopBackOff
Restart Count: 5
```

**Rozwiązanie:**
```bash
# Sprawdź logi (--previous = z poprzedniego uruchomienia)
kubectl logs <POD_NAME> --previous

# Sprawdź, czy PostgreSQL jest ready
kubectl get pods -l app=postgres

# Sprawdź connection string
kubectl describe configmap app-config
```

### Problem 3: Pending PVC

```
Status: Pending
Events: waiting for first consumer
```

**To jest NORMALNE!** PVC z `volumeBindingMode: WaitForFirstConsumer` zostanie bound dopiero gdy pod się uruchomi.

### Problem 4: Health Check Failed

```
Liveness probe failed: HTTP probe failed with statuscode: 503
```

**Rozwiązanie:**
- Zwiększ `initialDelaySeconds` w deployment.yaml (Spring Boot potrzebuje 60-90s na start)
- Sprawdź logi aplikacji: `kubectl logs <POD_NAME>`

---

## ETAP 7: Przydatne Komendy (Cheat Sheet)

### Podstawowe Operacje

```bash
# Status wszystkich resources
kubectl get all

# Status podów
kubectl get pods
kubectl describe pod <POD_NAME>
kubectl logs <POD_NAME>
kubectl logs -f <POD_NAME>  # follow (live)

# Status PVC i PV
kubectl get pvc
kubectl get pv

# Status services
kubectl get svc

# Exec do poda (shell)
kubectl exec -it <POD_NAME> -- /bin/sh
```

### Update Aplikacji (Nowa Wersja)

```bash
# 1. Build nowy obraz
docker build -t us-central1-docker.pkg.dev/weather-app-prod/weather-repo/weather-service:1.0.1 .

# 2. Push
docker push us-central1-docker.pkg.dev/weather-app-prod/weather-repo/weather-service:1.0.1

# 3. Update deployment
kubectl set image deployment/weather-service \
  weather-service=us-central1-docker.pkg.dev/weather-app-prod/weather-repo/weather-service:1.0.1

# 4. Monitoruj rollout
kubectl rollout status deployment/weather-service

# 5. Rollback (jeśli coś poszło nie tak)
kubectl rollout undo deployment/weather-service
```

### Scaling

```bash
# Zwiększ repliki
kubectl scale deployment weather-service --replicas=2

# UWAGA: Wymaga ReadWriteMany storage lub StatefulSet!
```

### Cleanup

```bash
# Usuń aplikację (zachowaj klaster)
kubectl delete deployment weather-service postgres
kubectl delete service weather-service-service postgres-service
kubectl delete configmap app-config
kubectl delete secret postgres-secret
kubectl delete pvc weather-data-pvc postgres-pvc

# Usuń klaster GKE (NIEODWRACALNE!)
gcloud container clusters delete weather-cluster --zone=us-central1-a

# Usuń Artifact Registry repo
gcloud artifacts repositories delete weather-repo --location=us-central1
```

---

## Podsumowanie Kosztów ($300 Trial)

**Przez 90 dni z $300 trial:**
- ✅ **Wszystko DARMOWE!** ($300 pokrywa wszystkie koszty)

**Po wyczerpaniu $300 trial:**
- GKE cluster (1 e2-small node): ~$13/miesiąc
- LoadBalancer: ~$18/miesiąc
- Persistent Disk (6GB total): $0 (w ramach 30GB free tier)
- Artifact Registry (< 0.5GB): $0 (free tier)
- **TOTAL: ~$31/miesiąc**

**Jak oszczędzać po trial:**
- Zmień LoadBalancer na NodePort: oszczędność $18/m
- Usuń klaster gdy nie używasz: oszczędność $13/m
- Użyj preemptible nodes: oszczędność ~$10/m

---

## Pliki do Modyfikacji - Podsumowanie

### Pliki do UTWORZENIA:
1. `k8s/weather-service/weather-pvc.yaml` - PVC dla weather-data

### Pliki do MODYFIKACJI:
1. **`k8s/weather-service/deployment.yaml`** (7 zmian):
    - Image URL → Artifact Registry
    - Usuń imagePullSecrets
    - replicas: 2 → 1
    - SecurityContext → non-root
    - Volume: hostPath → PVC
    - imagePullPolicy: Always → IfNotPresent
    - Resource limits (opcjonalnie)

2. **`k8s/postgres-configmap.yaml`** (1 zmiana):
    - WEATHER_API_BASE_URL: host.docker.internal → weather-api-service:8081

3. **`k8s/postgres/postgres-deployment.yaml`** (1 zmiana):
    - Volume: emptyDir → PVC

### Pliki do USUNIĘCIA z Git:
1. `k8s/postgres/postgres-secret.yaml` - NIE commitować secrets!

### Pliki BEZ ZMIAN:
1. `k8s/weather-service/service.yaml`
2. `k8s/postgres/postgres-service.yaml`
3. `k8s/postgres/postgres-pvc.yaml` (już istnieje i jest OK!)
4. `Dockerfile` (używany do build obrazu)

---

## Następne Kroki (Opcjonalne)

Po tym jak aplikacja działa, możesz:

1. **CI/CD Pipeline (GitHub Actions)**
    - Automatyczny build i deploy przy każdym push do mastera
    - Automatyczne testy przed deployem

2. **Monitoring (Cloud Monitoring)**
    - Dashboardy z metrykami CPU, RAM, requests
    - Alerty gdy coś idzie nie tak

3. **Custom Domain + SSL**
    - Kup domenę (np. weather-app.com)
    - Skonfiguruj Cloud DNS
    - Dodaj SSL certificate (Let's Encrypt)

4. **Horizontal Pod Autoscaler**
    - Automatyczne skalowanie przy dużym obciążeniu
    - `kubectl autoscale deployment weather-service --cpu-percent=70 --min=1 --max=3`

---

## Edukacyjne Zasoby

**Dokumentacja:**
- Google Kubernetes Engine: https://cloud.google.com/kubernetes-engine/docs
- Artifact Registry: https://cloud.google.com/artifact-registry/docs
- Kubernetes Basics: https://kubernetes.io/docs/tutorials/kubernetes-basics/

**Tutoriale:**
- GKE Quickstart: https://cloud.google.com/kubernetes-engine/docs/quickstart
- Deploy to GKE: https://cloud.google.com/kubernetes-engine/docs/how-to/deploying-workloads-overview

**Komendy Kubernetes:**
- Kubectl Cheat Sheet: https://kubernetes.io/docs/reference/kubectl/cheatsheet/

---

## Gotowy do Implementacji! 🚀

Plan jest kompletny i gotowy do wykonania. Zaczynamy od Etapu 1 (Przygotowanie Środowiska GCP).

**Czas realizacji:**
- Etap 1 (Setup GCP): ~15 minut
- Etap 2 (Build & Push): ~10 minut
- Etap 3 (Modyfikacja K8s): ~20 minut
- Etap 4 (Deployment): ~10 minut
- Etap 5 (Testowanie): ~10 minut
- **TOTAL: ~1-1.5 godziny**

Powodzenia! 🎉

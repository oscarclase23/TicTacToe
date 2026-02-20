# Tic-Tac-Toe

Una aplicación cliente-servidor de Tic-Tac-Toe. Juega contra otros jugadores en línea o contra la IA.

---

## 1. Compilación y Ejecución

### Requisitos
- Java 11 o superior
- Gradle (incluido con el proyecto)

### Compilar

**Windows:**
```bash
.\gradlew.bat build
```

**macOS/Linux:**
```bash
./gradlew build
```

### Ejecutar

**Iniciar Servidor** (en una terminal):
```bash
# Windows
.\gradlew.bat runServer

# macOS/Linux
./gradlew runServer
```

El servidor estará disponible en: `localhost:5678`

**Iniciar Cliente** (en otra terminal):
```bash
# Windows
.\gradlew.bat :composeApp:run

# macOS/Linux
./gradlew :composeApp:run
```

Se abrirá una ventana de 1200x800 píxeles.

### Configuración

**Servidor** - Edita `server.properties`:
```properties
server.host=0.0.0.0
server.port=5678
max.clients=10
```

---

## 2. Manual de Usuario

### Flujo de la Aplicación

```
Login → Menú Principal → Seleccionar Modo → Configuración → Juego → Récords
```

### Pantalla de Login
- Ingresa tu nombre de usuario
- Haz clic en "Continuar"

### Menú Principal
- **Jugar PVP**: Busca otro jugador en línea
- **Jugar vs IA**: Juega contra la computadora
- **Mis Récords**: Ve tus estadísticas
- **Salir**: Cierra la aplicación

### Pantalla de Configuración
Antes de jugar, configura:
- **Tamaño del Tablero**: 3x3, 4x4 o 5x5
- **Dificultad** (solo para IA): Fácil, Medio, Difícil
- **Tiempo por Turno**: 5 a 60 segundos
- **Total de Rondas**: Cuántas rondas jugar

### Pantalla de Juego

```
╔═══════════════════════════╗
║   Tic-Tac-Toe            ║
╠═══════════════════════════╣
║                           ║
║   Tablero     │  Puntos   ║
║   [X] [ ] [O] │  X: 1     ║
║   [ ] [X] [ ] │  O: 0     ║
║   [ ] [ ] [ ] │           ║
║               │  Ronda: 1 ║
║               │  Tiempo: 25║
║               │           ║
║  [Deshacer] [Rendirse]   ║
║                           ║
╚═══════════════════════════╝
```

**Cómo jugar:**
1. Haz clic en una casilla vacía para colocar tu símbolo (X u O)
2. El oponente juega automáticamente (IA) o espera su turno (PVP)
3. Forma 3 en raya (horizontal, vertical o diagonal) para ganar la ronda
4. Tras cada ronda, el tablero se reinicia
5. Gana el mayor número de rondas para ganar el partido

**Botones:**
- **Deshacer**: Revierte tu último movimiento
- **Rendirse**: Termina la ronda actual (pierdes automáticamente)
- **Volver al Menú**: Regresa (la partida se guarda en el servidor)

### Pantalla de Récords
Muestra tus estadísticas:
- Victorias, derrotas y empates
- Récord contra jugadores (PVP)
- Récord contra IA (PVE)
- Mejor racha de victorias

---

## 3. Arquitectura del Sistema

```
┌─────────────────────────────────────┐
│        CLIENTE (Interfaz Gráfica)   │
│  • Pantallas Compose                │
│  • GameClient (lógica del cliente)  │
│  • NetworkClient (conexión)         │
└────────────────┬────────────────────┘
                 │ TCP/IP (Puerto 5678)
                 │ JSON
                 ▼
┌─────────────────────────────────────┐
│          SERVIDOR (Backend)         │
│  • GameServer (gestor principal)    │
│  • GameSession (partida)            │
│  • GameAI (inteligencia artificial) │
│  • RecordsManager (estadísticas)    │
└─────────────────────────────────────┘
```

### Componentes

| Componente | Qué hace |
|-----------|----------|
| **GameClient** | Maneja la interfaz del usuario |
| **NetworkClient** | Comunica con el servidor |
| **GameServer** | Gestiona todas las partidas |
| **GameSession** | Controla una partida específica |
| **GameAI** | Genera los movimientos de IA |
| **RecordsManager** | Guarda las estadísticas |

---

## 4. Protocolo de Comunicación

La comunicación entre cliente y servidor usa mensajes en formato **JSON**.

### Mensajes Principales

#### 1. CONNECT (Conexión)
**Cliente envía:**
```json
{
  "type": "CONNECT",
  "playerName": "Juan"
}
```

**Servidor responde:**
```json
{
  "type": "CONNECT",
  "playerId": "abc123",
  "message": "Conectado exitosamente"
}
```

---

#### 2. JOIN_QUEUE (Buscar Jugador PVP)
**Cliente envía:**
```json
{
  "type": "JOIN_QUEUE",
  "playerName": "Juan",
  "preferredBoardSize": 3,
  "timeLimit": 30
}
```

**Servidor responde (cuando encuentra oponente):**
```json
{
  "type": "GAME_FOUND",
  "matchId": "match-001",
  "opponentName": "María"
}
```

---

#### 3. CREATE_GAME (Nueva Partida vs IA)
**Cliente envía:**
```json
{
  "type": "CREATE_GAME",
  "gameConfig": {
    "boardSize": 3,
    "totalRounds": 3,
    "difficulty": "MEDIUM",
    "timeLimit": 30
  }
}
```

**Servidor responde:**
```json
{
  "type": "GAME_FOUND",
  "matchId": "match-002",
  "opponentName": "AI"
}
```

---

#### 4. GAME_STATE (Estado del Tablero)
**Servidor envía:**
```json
{
  "type": "GAME_STATE",
  "matchId": "match-001",
  "board": [
    ["X", " ", "O"],
    [" ", "X", " "],
    [" ", " ", " "]
  ],
  "currentPlayer": "X",
  "scores": {"Juan": 1, "María": 0}
}
```

---

#### 5. MAKE_MOVE (Hacer un Movimiento)
**Cliente envía:**
```json
{
  "type": "MAKE_MOVE",
  "matchId": "match-001",
  "position": {
    "row": 0,
    "col": 1
  }
}
```

**Servidor responde:**
```json
{
  "type": "MOVE_RESULT",
  "valid": true,
  "player": "X"
}
```

---

#### 6. ROUND_END (Fin de Ronda)
**Servidor envía:**
```json
{
  "type": "ROUND_END",
  "winner": "Juan",
  "reason": "Tres en raya"
}
```

---

#### 7. MATCH_END (Fin del Partido)
**Servidor envía:**
```json
{
  "type": "MATCH_END",
  "winner": "Juan",
  "finalScore": {"Juan": 2, "María": 1}
}
```

---

#### 8. DISCONNECT (Desconexión)
**Cliente envía:**
```json
{
  "type": "DISCONNECT",
  "playerId": "abc123"
}
```

---

### Otros Mensajes

- **UNDO_REQUEST** - Solicita deshacer el movimiento
- **RECONNECT** - Reconecta a una partida anterior
- **OPPONENT_DISCONNECTED** - El oponente se desconectó
- **ERROR** - El servidor informa un error

---

## Notas Importantes

- Los registros de jugadores se guardan en `records.json`
- Las partidas activas se guardan en `active_games.json`
- Los logs del cliente se guardan en `client.log`
- Las reconexiones automáticas se activan si se pierde la conexión
- El puerto 5678 debe estar disponible

---

**Desarrollado con Kotlin Multiplatform | © 2024**

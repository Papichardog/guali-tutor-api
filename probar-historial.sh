#!/usr/bin/env bash
# Pruebas de /api/historial por HTTPS.
# Uso (desde la raíz del proyecto, con la API corriendo):   bash probar-historial.sh
#
# No toca tu historial real: todo lo que crea tiene el prefijo "zz-prueba-api-" en nombreLeccion, y solo borra
# registros y lecciones con ese prefijo (al empezar y al terminar). Al final comprueba que el número de registros
# reales no cambió. Usa la API de lecciones solo para crear/borrar lecciones de prueba con ese mismo prefijo.

BASE="${BASE:-https://localhost:8443}"
CERT="${CERT:-certs/guali-dev.crt}"
PREFIJO="zz-prueba-api-"
OK=0
FALLA=0
CUERPO=$(mktemp)
CABECERAS=$(mktemp)
trap 'rm -f "$CUERPO" "$CABECERAS"' EXIT

# probar "descripción" "códigos esperados (separados por |)" "texto que debe contener la respuesta" MÉTODO RUTA [cuerpo JSON]
#   - ESPERA_LOCATION=/ruta   también comprueba la cabecera Location.
#   - NO_CONTIENE="texto"     comprueba además que la respuesta NO contenga ese texto.
probar() {
  local desc="$1" esperado="$2" contiene="$3" metodo="$4" ruta="$5"
  local args=(-s --cacert "$CERT" -X "$metodo" -o "$CUERPO" -D "$CABECERAS" -w '%{http_code}')
  if [ "$#" -ge 6 ]; then
    args+=(-H 'Content-Type: application/json' --data-binary "$6")
  fi
  local codigo; codigo=$(curl "${args[@]}" "$BASE$ruta")
  local respuesta; respuesta=$(cat "$CUERPO")
  local bien=1
  [[ "|$esperado|" == *"|$codigo|"* ]] || bien=0
  [[ "$respuesta" == *"$contiene"* ]] || bien=0
  if [ -n "${NO_CONTIENE:-}" ] && [[ "$respuesta" == *"$NO_CONTIENE"* ]]; then bien=0; fi
  if [ -n "${ESPERA_LOCATION:-}" ] && ! grep -qi "^location: $BASE$ESPERA_LOCATION" "$CABECERAS"; then bien=0; fi
  if [ "$bien" = 1 ]; then
    echo "[OK]    $desc -> $codigo"; OK=$((OK + 1))
  else
    echo "[FALLA] $desc"
    echo "          esperaba: HTTP $esperado y que contenga: $contiene ${NO_CONTIENE:+y que NO contenga $NO_CONTIENE }${ESPERA_LOCATION:+y Location $ESPERA_LOCATION}"
    echo "          obtuve:   HTTP $codigo  ${respuesta:0:400}"
    FALLA=$((FALLA + 1))
  fi
}

verificar() { # verificar "descripción" condición(0=ok)
  if [ "$2" = 0 ]; then echo "[OK]    $1"; OK=$((OK + 1)); else echo "[FALLA] $1"; FALLA=$((FALLA + 1)); fi
}

id_de_la_respuesta() { grep -o '"id":[0-9]*' "$CUERPO" | head -1 | cut -d: -f2; }
campo_de_la_respuesta() { grep -o "\"$1\":\"[^\"]*\"" "$CUERPO" | head -1 | cut -d'"' -f4; }

# Cuerpos: hist NOMBRE [campos extra, p. ej. '"leccionId":3,"estado":"Completada"']   y   leccion NOMBRE
hist() { printf '{"nombreLeccion":"%s"%s}' "$1" "${2:+,$2}"; }
leccion() { printf '{"nombre":"%s","contenidoMarkdown":"# Prueba","objetivo":"COMPILAR_EXITOSO"}' "$1"; }
# Cuerpo completo de un PUT: completo LECCION_ID(o null) NOMBRE HORA ESTADO
completo() { printf '{"leccionId":%s,"nombreLeccion":"%s","horaApertura":"%s","estado":"%s"}' "$1" "$2" "$3" "$4"; }

contar_reales() {   # registros de historial que NO son de prueba
  curl -s --cacert "$CERT" "$BASE/api/historial" | grep -o '"nombreLeccion":"[^"]*"' | grep -vc "\"nombreLeccion\":\"$PREFIJO"
}

borrar_pruebas() {
  curl -s --cacert "$CERT" "$BASE/api/historial" | grep -o "\"id\":[0-9]*,\"leccionId\":[^,]*,\"nombreLeccion\":\"$PREFIJO[^\"]*\"" | sed -E 's/"id":([0-9]+),.*/\1/' |
  while read -r id; do curl -s --cacert "$CERT" -X DELETE -o /dev/null "$BASE/api/historial/$id"; done
  curl -s --cacert "$CERT" "$BASE/api/lecciones" | grep -o "\"id\":[0-9]*,\"nombre\":\"$PREFIJO[^\"]*\"" | sed -E 's/"id":([0-9]+),.*/\1/' |
  while read -r id; do curl -s --cacert "$CERT" -X DELETE -o /dev/null "$BASE/api/lecciones/$id"; done
}

echo "API: $BASE   certificado: $CERT"
if [ ! -f "$CERT" ]; then echo "No existe $CERT: ejecuta el script desde la raíz del proyecto."; exit 2; fi
if ! curl -s --cacert "$CERT" -o /dev/null "$BASE/api/historial"; then
  echo "No se pudo conectar a $BASE (¿está corriendo la API?)."; exit 2
fi

echo; echo "== Limpieza previa (solo registros y lecciones con prefijo $PREFIJO) =="
borrar_pruebas
REALES_ANTES=$(contar_reales)
echo "   registros reales de historial (no se tocarán): $REALES_ANTES"

echo; echo "== Lectura (no modifica nada) =="
probar "GET lista"                          200 '['                                          GET /api/historial
probar "GET id inexistente"                 404 '"codigo":"RECURSO_NO_ENCONTRADO"'           GET /api/historial/123456789
probar "GET id no numérico"                 400 '"codigo":"PARAMETRO_INVALIDO"'              GET /api/historial/abc
probar "GET id 0"                           400 '"codigo":"PARAMETRO_INVALIDO"'              GET /api/historial/0

echo; echo "== Preparación: una lección de prueba para enlazar =="
probar "POST lección de prueba"             201 '"id":'                                      POST /api/lecciones "$(leccion ${PREFIJO}leccion)"
ID_L=$(id_de_la_respuesta)

echo; echo "== POST: registrar intentos =="
probar "POST solo con nombreLeccion (lo que hará Swing)" 201 '"estado":"Pendiente"'          POST /api/historial "$(hist ${PREFIJO}local)"
ID_1=$(id_de_la_respuesta)
if grep -qi "^location: $BASE/api/historial/$ID_1" "$CABECERAS"; then   # las cabeceras siguen siendo las del POST
  verificar "   el POST devolvió Location: $BASE/api/historial/$ID_1" 0
else
  verificar "   el POST debía devolver Location: $BASE/api/historial/$ID_1" 1
fi
probar "   sin leccionId queda como lección local (null)" 200 '"leccionId":null'               GET /api/historial/$ID_1
HORA=$(campo_de_la_respuesta horaApertura)
if [[ "$HORA" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}$ ]]; then
  verificar "   la hora la puso el servidor, con formato yyyy-MM-ddTHH:mm:ss ($HORA)" 0
else
  verificar "   la hora del servidor debía tener formato yyyy-MM-ddTHH:mm:ss, obtuve '$HORA'" 1
fi
probar "POST enlazado a una lección de la BD" 201 "\"leccionId\":$ID_L"                      POST /api/historial "$(hist ${PREFIJO}bd "\"leccionId\":$ID_L")"
ID_2=$(id_de_la_respuesta)
probar "POST con estado y hora explícitos"  201 '"estado":"Completada"'                      POST /api/historial "$(hist ${PREFIJO}explicito '"estado":"Completada","horaApertura":"2001-02-03T04:05:06"')"
ID_3=$(id_de_la_respuesta)
probar "   la hora enviada se respeta"      200 '"horaApertura":"2001-02-03T04:05:06"'       GET /api/historial/$ID_3
probar "POST con fracción de segundo: se descarta" 201 '"horaApertura":"2001-02-03T04:05:06"' POST /api/historial "$(hist ${PREFIJO}fraccion '"horaApertura":"2001-02-03T04:05:06.789"')"

echo; echo "== POST: validaciones =="
probar "nombreLeccion en blanco"            400 'no puede estar en blanco'                   POST /api/historial "$(hist '   ')"
probar "sin nombreLeccion"                  400 '"campo":"nombreLeccion"'                    POST /api/historial '{}'
probar "leccionId 0"                        400 'mayor o igual a 1'                          POST /api/historial "$(hist ${PREFIJO}v '"leccionId":0')"
probar "leccionId que no existe"            400 '"codigo":"REFERENCIA_INVALIDA"'             POST /api/historial "$(hist ${PREFIJO}v '"leccionId":999999999')"
probar "leccionId no numérico"              400 '"campo":"leccionId"'                        POST /api/historial "$(hist ${PREFIJO}v '"leccionId":"a"')"
probar "estado inválido"                    400 'debe ser uno de: Pendiente, Completada'     POST /api/historial "$(hist ${PREFIJO}v '"estado":"Terminada"')"
probar "estado null"                        400 'no puede ser null'                          POST /api/historial "$(hist ${PREFIJO}v '"estado":null')"
probar "hora con otro formato"              400 '"campo":"horaApertura"'                     POST /api/historial "$(hist ${PREFIJO}v '"horaApertura":"2026-10-14 21:30:05"')"
probar "hora imposible (mes 13)"            400 '"campo":"horaApertura"'                     POST /api/historial "$(hist ${PREFIJO}v '"horaApertura":"2026-13-45T00:00:00"')"
probar "hora con zona horaria (Z)"          400 '"campo":"horaApertura"'                     POST /api/historial "$(hist ${PREFIJO}v '"horaApertura":"2026-10-14T21:30:05Z"')"
probar "propiedad desconocida"              400 '"codigo":"CUERPO_INVALIDO"'                 POST /api/historial "$(hist ${PREFIJO}v '"otro":1')"
probar "id en el cuerpo (lo asigna el servidor)" 400 '"codigo":"CUERPO_INVALIDO"'            POST /api/historial "$(hist ${PREFIJO}v '"id":5')"
probar "JSON malformado"                    400 '"codigo":"CUERPO_INVALIDO"'                 POST /api/historial '{'
CODIGO=$(curl -s --cacert "$CERT" -X POST -o "$CUERPO" -w '%{http_code}' -d "$(hist x)" "$BASE/api/historial")
if [ "$CODIGO" = 415 ]; then verificar "POST sin Content-Type: application/json -> 415" 0; else verificar "POST sin Content-Type -> esperaba 415, obtuve $CODIGO" 1; fi

echo; echo "== GET: el listado va ordenado por horaApertura y luego por id =="
probar "POST orden-a (hora 10:00)"          201 '"id":'                                      POST /api/historial "$(hist ${PREFIJO}orden-a '"horaApertura":"2001-01-01T10:00:00"')"
probar "POST orden-b (hora 09:00)"          201 '"id":'                                      POST /api/historial "$(hist ${PREFIJO}orden-b '"horaApertura":"2001-01-01T09:00:00"')"
probar "POST orden-c (misma hora que b)"    201 '"id":'                                      POST /api/historial "$(hist ${PREFIJO}orden-c '"horaApertura":"2001-01-01T09:00:00"')"
ORDEN=$(curl -s --cacert "$CERT" "$BASE/api/historial" | grep -o "\"nombreLeccion\":\"${PREFIJO}orden-[abc]\"" | sed -E 's/.*orden-([abc])"/\1/' | tr -d '\n')
if [ "$ORDEN" = "bca" ]; then verificar "orden correcto: b, c (empate resuelto por id), a" 0; else verificar "orden incorrecto: esperaba bca, obtuve '$ORDEN'" 1; fi
probar "GET detalle de un registro"         200 "\"nombreLeccion\":\"${PREFIJO}local\""       GET /api/historial/$ID_1

echo; echo "== PATCH: marcar Completada y la regla de no regresión =="
probar "PATCH estado Completada"            200 '"estado":"Completada"'                      PATCH /api/historial/$ID_1 '{"estado":"Completada"}'
probar "PATCH Completada otra vez (idempotente)" 200 '"estado":"Completada"'                 PATCH /api/historial/$ID_1 '{"estado":"Completada"}'
probar "PATCH Completada -> Pendiente: 409" 409 '"codigo":"ESTADO_NO_REGRESIVO"'             PATCH /api/historial/$ID_1 '{"estado":"Pendiente"}'
probar "   el 409 no modificó el registro"  200 '"estado":"Completada"'                      GET /api/historial/$ID_1
probar "PUT Completada -> Pendiente: 409"   409 '"codigo":"ESTADO_NO_REGRESIVO"'             PUT /api/historial/$ID_1 "$(completo null ${PREFIJO}local 2001-01-01T00:00:00 Pendiente)"
probar "regresión + cambio de nombre: 409 y NADA se aplica" 409 '"codigo":"ESTADO_NO_REGRESIVO"' PATCH /api/historial/$ID_1 '{"estado":"Pendiente","nombreLeccion":"NO-DEBE-CAMBIAR"}'
probar "   el nombre no cambió (atómico)"   200 "\"nombreLeccion\":\"${PREFIJO}local\""         GET /api/historial/$ID_1
probar "cambiar el nombre de un Completada sí se permite" 200 "\"nombreLeccion\":\"${PREFIJO}local-renombrado\"" PATCH /api/historial/$ID_1 "{\"nombreLeccion\":\"${PREFIJO}local-renombrado\"}"
probar "PATCH cuerpo vacío"                 400 '"campo":"(cuerpo)"'                         PATCH /api/historial/$ID_1 '{}'
probar "PATCH estado null"                  400 'no puede ser null'                          PATCH /api/historial/$ID_1 '{"estado":null}'
probar "PATCH propiedad desconocida"        400 '"codigo":"CUERPO_INVALIDO"'                 PATCH /api/historial/$ID_1 '{"estdo":"Completada"}'
probar "PATCH estado con tipo inválido"     400 '"campo":"estado"'                           PATCH /api/historial/$ID_1 '{"estado":123}'
probar "PATCH id inexistente"               404 '"codigo":"RECURSO_NO_ENCONTRADO"'           PATCH /api/historial/123456789 '{"estado":"Completada"}'
probar "PATCH id no numérico"               400 '"codigo":"PARAMETRO_INVALIDO"'              PATCH /api/historial/abc '{"estado":"Completada"}'
probar "PATCH id 0"                         400 '"codigo":"PARAMETRO_INVALIDO"'              PATCH /api/historial/0 '{"estado":"Completada"}'

echo; echo "== PATCH: lección enlazada y otros campos (registro $ID_2, Pendiente) =="
probar "PATCH Pendiente sobre Pendiente es válido" 200 '"estado":"Pendiente"'                PATCH /api/historial/$ID_2 '{"estado":"Pendiente"}'
probar "PATCH solo la hora no toca lo demás" 200 '"horaApertura":"2001-05-06T07:08:09"'      PATCH /api/historial/$ID_2 '{"horaApertura":"2001-05-06T07:08:09"}'
probar "   (el vínculo con la lección sigue)" 200 "\"leccionId\":$ID_L"                       GET /api/historial/$ID_2
probar "PATCH hora inválida"                400 '"campo":"horaApertura"'                     PATCH /api/historial/$ID_2 '{"horaApertura":"ayer"}'
probar "PATCH leccionId:null desvincula"    200 '"leccionId":null'                           PATCH /api/historial/$ID_2 '{"leccionId":null}'
probar "PATCH leccionId vuelve a vincular"  200 "\"leccionId\":$ID_L"                        PATCH /api/historial/$ID_2 "{\"leccionId\":$ID_L}"
probar "PATCH leccionId inexistente"        400 '"codigo":"REFERENCIA_INVALIDA"'             PATCH /api/historial/$ID_2 '{"leccionId":999999999}'
probar "   el vínculo no cambió"            200 "\"leccionId\":$ID_L"                        GET /api/historial/$ID_2

echo; echo "== PUT: reemplazo completo =="
probar "PUT reemplaza los 4 campos"         200 '"leccionId":null'                           PUT /api/historial/$ID_2 "$(completo null ${PREFIJO}reemplazado 2002-02-02T02:02:02 Completada)"
probar "   y el detalle lo confirma"        200 '"horaApertura":"2002-02-02T02:02:02"'       GET /api/historial/$ID_2
probar "PUT Completada -> Completada es válido" 200 '"estado":"Completada"'                  PUT /api/historial/$ID_2 "$(completo null ${PREFIJO}reemplazado 2002-02-02T02:02:02 Completada)"
probar "PUT Completada -> Pendiente: 409"   409 '"codigo":"ESTADO_NO_REGRESIVO"'             PUT /api/historial/$ID_2 "$(completo null ${PREFIJO}reemplazado 2002-02-02T02:02:02 Pendiente)"
probar "PUT con estado inválido"            400 'debe ser uno de: Pendiente, Completada'     PUT /api/historial/$ID_2 "$(completo null ${PREFIJO}reemplazado 2002-02-02T02:02:02 Terminada)"
probar "PUT con leccionId inexistente"      400 '"codigo":"REFERENCIA_INVALIDA"'             PUT /api/historial/$ID_2 "$(completo 999999999 ${PREFIJO}reemplazado 2002-02-02T02:02:02 Completada)"
probar "PUT sin leccionId"                  400 '"campo":"leccionId"'                        PUT /api/historial/$ID_2 "{\"nombreLeccion\":\"x\",\"horaApertura\":\"2002-02-02T02:02:02\",\"estado\":\"Completada\"}"
probar "PUT con faltantes: los informa todos" 400 '"campo":"estado"'                         PUT /api/historial/$ID_2 '{"leccionId":null}'
probar "PUT con id en el cuerpo"            400 '"codigo":"CUERPO_INVALIDO"'                 PUT /api/historial/$ID_2 "{\"id\":$ID_2,\"leccionId\":null,\"nombreLeccion\":\"x\",\"horaApertura\":\"2002-02-02T02:02:02\",\"estado\":\"Completada\"}"
probar "PUT sobre un id inexistente (no crea)" 404 '"codigo":"RECURSO_NO_ENCONTRADO"'        PUT /api/historial/123456789 "$(completo null ${PREFIJO}fantasma 2002-02-02T02:02:02 Pendiente)"
probar "   y no creó nada"                  404 '"codigo":"RECURSO_NO_ENCONTRADO"'           GET /api/historial/123456789
probar "PUT id 0"                           400 '"codigo":"PARAMETRO_INVALIDO"'              PUT /api/historial/0 "$(completo null ${PREFIJO}x 2002-02-02T02:02:02 Pendiente)"

echo; echo "== Eliminar una lección conserva el historial (clave foránea ON DELETE SET NULL de tu BD) =="
probar "POST lección que se va a eliminar"  201 '"id":'                                      POST /api/lecciones "$(leccion ${PREFIJO}fk)"
ID_F=$(id_de_la_respuesta)
probar "POST registro enlazado a esa lección, ya Completada" 201 "\"leccionId\":$ID_F"       POST /api/historial "$(hist ${PREFIJO}fk "\"leccionId\":$ID_F,\"estado\":\"Completada\"")"
ID_H=$(id_de_la_respuesta)
probar "DELETE de la lección"               204 ''                                           DELETE /api/lecciones/$ID_F
probar "el registro SIGUE existiendo con leccionId null" 200 '"leccionId":null'              GET /api/historial/$ID_H
probar "   conserva su nombreLeccion"       200 "\"nombreLeccion\":\"${PREFIJO}fk\""          GET /api/historial/$ID_H
probar "   conserva su estado"              200 '"estado":"Completada"'                      GET /api/historial/$ID_H
probar "POST lección que NO se va a eliminar" 201 '"id":'                                    POST /api/lecciones "$(leccion ${PREFIJO}sobrevive)"
ID_G=$(id_de_la_respuesta)
probar "POST registro enlazado a ella"      201 "\"leccionId\":$ID_G"                        POST /api/historial "$(hist ${PREFIJO}sobrevive "\"leccionId\":$ID_G")"
ID_HG=$(id_de_la_respuesta)
probar "DELETE del registro de historial"   204 ''                                           DELETE /api/historial/$ID_HG
probar "   la lección NO se borra con su registro" 200 "\"nombre\":\"${PREFIJO}sobrevive\""   GET /api/lecciones/$ID_G

echo; echo "== DELETE =="
probar "DELETE registro"                    204 ''                                           DELETE /api/historial/$ID_1
probar "GET registro ya no existe"          404 '"codigo":"RECURSO_NO_ENCONTRADO"'           GET /api/historial/$ID_1
probar "DELETE otra vez"                    404 '"codigo":"RECURSO_NO_ENCONTRADO"'           DELETE /api/historial/$ID_1
probar "DELETE id 0"                        400 '"codigo":"PARAMETRO_INVALIDO"'              DELETE /api/historial/0
probar "DELETE id no numérico"              400 '"codigo":"PARAMETRO_INVALIDO"'              DELETE /api/historial/abc

echo; echo "== Limpieza final =="
borrar_pruebas
NO_CONTIENE="$PREFIJO" \
probar "no queda ningún registro de prueba" 200 '['                                          GET /api/historial
REALES_DESPUES=$(contar_reales)
if [ "$REALES_ANTES" = "$REALES_DESPUES" ]; then verificar "tus $REALES_ANTES registros reales de historial siguen intactos" 0
else verificar "el número de registros reales cambió: antes $REALES_ANTES, después $REALES_DESPUES" 1; fi

echo; echo "Resultado: $OK correctas, $FALLA con fallo."
[ "$FALLA" = 0 ]

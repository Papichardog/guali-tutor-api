#!/usr/bin/env bash
# Pruebas de /api/configuracion por HTTPS.
# Uso (desde la raíz del proyecto, con la API corriendo):   bash probar-configuracion.sh
#
# No toca tu configuración real (id 1): todo lo que escribe usa los ids de prueba 98 y 99,
# que borra al empezar y al terminar.

BASE="${BASE:-https://localhost:8443}"
CERT="${CERT:-certs/guali-dev.crt}"
OK=0
FALLA=0
CUERPO=$(mktemp)
CABECERAS=$(mktemp)
trap 'rm -f "$CUERPO" "$CABECERAS"' EXIT

# probar "descripción" "códigos esperados (separados por |)" "texto que debe contener la respuesta" MÉTODO RUTA [cuerpo JSON]
#   - Si se pasa ESPERA_LOCATION=/ruta, también comprueba la cabecera Location.
probar() {
  local desc="$1" esperado="$2" contiene="$3" metodo="$4" ruta="$5"
  local args=(-s --cacert "$CERT" -X "$metodo" -o "$CUERPO" -D "$CABECERAS" -w '%{http_code}')
  if [ "$#" -ge 6 ]; then
    args+=(-H 'Content-Type: application/json' -d "$6")
  fi
  local codigo; codigo=$(curl "${args[@]}" "$BASE$ruta")
  local respuesta; respuesta=$(cat "$CUERPO")
  local bien=1
  [[ "|$esperado|" == *"|$codigo|"* ]] || bien=0
  [[ "$respuesta" == *"$contiene"* ]] || bien=0
  if [ -n "${ESPERA_LOCATION:-}" ] && ! grep -qi "^location: $BASE$ESPERA_LOCATION" "$CABECERAS"; then bien=0; fi
  if [ "$bien" = 1 ]; then
    echo "[OK]    $desc -> $codigo"; OK=$((OK + 1))
  else
    echo "[FALLA] $desc"
    echo "          esperaba: HTTP $esperado y que contenga: $contiene ${ESPERA_LOCATION:+y Location $ESPERA_LOCATION}"
    echo "          obtuve:   HTTP $codigo  $respuesta"
    FALLA=$((FALLA + 1))
  fi
}

echo "API: $BASE   certificado: $CERT"
if [ ! -f "$CERT" ]; then echo "No existe $CERT: ejecuta el script desde la raíz del proyecto."; exit 2; fi
if ! curl -s --cacert "$CERT" -o /dev/null "$BASE/api/configuracion"; then
  echo "No se pudo conectar a $BASE (¿está corriendo la API?)."; exit 2
fi

echo; echo "== Limpieza previa (ids de prueba 98 y 99) =="
curl -s --cacert "$CERT" -X DELETE -o /dev/null "$BASE/api/configuracion/98"
curl -s --cacert "$CERT" -X DELETE -o /dev/null "$BASE/api/configuracion/99"

echo; echo "== Lectura y errores de ruta (no modifican nada) =="
probar "GET lista"                          200 '['                                   GET /api/configuracion
probar "GET /1 (tu configuración real; 404 si aún no la has creado)" "200|404" ''     GET /api/configuracion/1
probar "GET id inexistente"                 404 '"codigo":"RECURSO_NO_ENCONTRADO"'    GET /api/configuracion/12345
probar "GET id no numérico"                 400 '"codigo":"PARAMETRO_INVALIDO"'       GET /api/configuracion/abc
probar "GET id 0"                           400 '"codigo":"PARAMETRO_INVALIDO"'       GET /api/configuracion/0
probar "GET ruta inexistente (misma estructura de error)" 404 '"codigo":"RECURSO_NO_ENCONTRADO"' GET /api/nada

echo; echo "== POST =="
ESPERA_LOCATION=/api/configuracion/99 \
probar "POST crea (id 99, solo JDK)"        201 '"leccionesRootPath":null'            POST /api/configuracion '{"id":99,"jdkPath":"/tmp/jdk-prueba"}'
probar "POST con id repetido"               409 '"codigo":"CONFIGURACION_DUPLICADA"'  POST /api/configuracion '{"id":99,"jdkPath":"/otro"}'
probar "POST id 0"                          400 '"campo":"id"'                        POST /api/configuracion '{"id":0}'
probar "POST sin id"                        400 '"campo":"id"'                        POST /api/configuracion '{}'
probar "POST propiedad desconocida"         400 '"codigo":"CUERPO_INVALIDO"'          POST /api/configuracion '{"id":97,"otro":1}'
probar "POST JSON malformado"               400 '"codigo":"CUERPO_INVALIDO"'          POST /api/configuracion '{'
CODIGO=$(curl -s --cacert "$CERT" -X POST -o "$CUERPO" -w '%{http_code}' -d '{"id":97}' "$BASE/api/configuracion")
if [ "$CODIGO" = 415 ] && grep -q 'application/json' "$CUERPO"; then echo "[OK]    POST sin Content-Type: application/json -> 415"; OK=$((OK + 1))
else echo "[FALLA] POST sin Content-Type -> esperaba 415, obtuve $CODIGO $(cat "$CUERPO")"; FALLA=$((FALLA + 1)); fi

echo; echo "== PATCH: lo ausente se conserva, null borra =="
probar "PATCH solo la carpeta de lecciones (el JDK se conserva)" 200 '"jdkPath":"/tmp/jdk-prueba"' PATCH /api/configuracion/99 '{"leccionesRootPath":"/tmp/lecciones-prueba"}'
probar "PATCH jdkPath:null lo borra"        200 '"jdkPath":null'                      PATCH /api/configuracion/99 '{"jdkPath":null}'
probar "GET confirma que la carpeta de lecciones se conservó" 200 '"leccionesRootPath":"/tmp/lecciones-prueba"' GET /api/configuracion/99
probar "PATCH cuerpo vacío"                 400 '"campo":"(cuerpo)"'                  PATCH /api/configuracion/99 '{}'
probar "PATCH tipo inválido"                400 'debe ser texto o null'               PATCH /api/configuracion/99 '{"jdkPath":123}'
probar "PATCH propiedad desconocida"        400 '"codigo":"CUERPO_INVALIDO"'          PATCH /api/configuracion/99 '{"jdkpath":"x"}'
probar "PATCH id inexistente"               404 '"codigo":"RECURSO_NO_ENCONTRADO"'    PATCH /api/configuracion/12345 '{"jdkPath":"x"}'

echo; echo "== PUT: reemplazo completo =="
probar "PUT reemplaza todo (la carpeta de lecciones queda en null)" 200 '"leccionesRootPath":null' PUT /api/configuracion/99 '{"jdkPath":"/tmp/otro-jdk","leccionesRootPath":null}'
probar "PUT sin uno de los campos"          400 'es obligatorio'                      PUT /api/configuracion/99 '{"jdkPath":"/tmp/x"}'
probar "PUT con id en el cuerpo"            400 '"codigo":"CUERPO_INVALIDO"'          PUT /api/configuracion/99 '{"id":99,"jdkPath":null,"leccionesRootPath":null}'
ESPERA_LOCATION=/api/configuracion/98 \
probar "PUT sobre un id nuevo lo crea"      201 '"id":98'                             PUT /api/configuracion/98 '{"jdkPath":"/tmp/jdk-98","leccionesRootPath":null}'

echo; echo "== DELETE y limpieza =="
probar "DELETE 99"                          204 ''                                    DELETE /api/configuracion/99
probar "DELETE 99 otra vez"                 404 '"codigo":"RECURSO_NO_ENCONTRADO"'    DELETE /api/configuracion/99
probar "DELETE 98"                          204 ''                                    DELETE /api/configuracion/98
probar "GET 99 ya no existe"                404 '"codigo":"RECURSO_NO_ENCONTRADO"'    GET /api/configuracion/99

echo; echo "== HTTPS obligatorio =="
CODIGO=$(curl -s -m 5 -o /dev/null -w '%{http_code}' "http://${BASE#https://}/api/configuracion")
if [ "$CODIGO" = 000 ] || [ "$CODIGO" = 400 ]; then echo "[OK]    HTTP plano contra el puerto 8443 no funciona (código $CODIGO)"; OK=$((OK + 1))
else echo "[FALLA] HTTP plano respondió $CODIGO: no debería funcionar"; FALLA=$((FALLA + 1)); fi

echo; echo "Resultado: $OK correctas, $FALLA con fallo."
[ "$FALLA" = 0 ]

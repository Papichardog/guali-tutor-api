#!/usr/bin/env bash
# Pruebas de /api/lecciones (con imágenes) por HTTPS.
# Uso (desde la raíz del proyecto, con la API corriendo):   bash probar-lecciones.sh
#
# No toca tus lecciones reales: todo lo que crea tiene el prefijo "zz-prueba-api-", y solo borra lecciones con ese
# prefijo (al empezar y al terminar). Sobre las demás, únicamente lee.

BASE="${BASE:-https://localhost:8443}"
CERT="${CERT:-certs/guali-dev.crt}"
PREFIJO="zz-prueba-api-"
OK=0
FALLA=0
CUERPO=$(mktemp)
CABECERAS=$(mktemp)
TMP=$(mktemp)
trap 'rm -f "$CUERPO" "$CABECERAS" "$TMP"' EXIT

# Dos imágenes diminutas y válidas: un PNG de 1x1 y un GIF de 1x1.
PNG="iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=="
GIF="R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7"

# probar "descripción" "códigos esperados (separados por |)" "texto que debe contener la respuesta" MÉTODO RUTA [cuerpo JSON o @archivo]
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

# Resultado de comprobaciones propias (las que no encajan en probar)
verificar() { # verificar "descripción" condición(0=ok)
  if [ "$2" = 0 ]; then echo "[OK]    $1"; OK=$((OK + 1)); else echo "[FALLA] $1"; FALLA=$((FALLA + 1)); fi
}

# id de la última respuesta (primer "id":N del cuerpo) y valor de un campo de texto del cuerpo
id_de_la_respuesta() { grep -o '"id":[0-9]*' "$CUERPO" | head -1 | cut -d: -f2; }
campo_de_la_respuesta() { grep -o "\"$1\":\"[^\"]*\"" "$CUERPO" | head -1 | cut -d'"' -f4; }

# Cuerpo de una lección: lec NOMBRE OBJETIVO IMAGENES_JSON(opcional)
lec() { printf '{"nombre":"%s","contenidoMarkdown":"# Prueba\\n\\n![x](a.png)\\n","objetivo":"%s","imagenes":%s}' "$1" "$2" "${3:-[]}"; }
imagen() { printf '{"nombre":"%s","mimeType":"%s","contenidoBase64":"%s"}' "$1" "$2" "$3"; }

borrar_pruebas() {
  curl -s --cacert "$CERT" "$BASE/api/lecciones" | grep -o "\"id\":[0-9]*,\"nombre\":\"$PREFIJO[^\"]*\"" | sed -E 's/"id":([0-9]+),.*/\1/' |
  while read -r id; do curl -s --cacert "$CERT" -X DELETE -o /dev/null "$BASE/api/lecciones/$id"; done
}

echo "API: $BASE   certificado: $CERT"
if [ ! -f "$CERT" ]; then echo "No existe $CERT: ejecuta el script desde la raíz del proyecto."; exit 2; fi
if ! curl -s --cacert "$CERT" -o /dev/null "$BASE/api/lecciones"; then
  echo "No se pudo conectar a $BASE (¿está corriendo la API?)."; exit 2
fi

echo; echo "== Limpieza previa (solo lecciones con prefijo $PREFIJO) =="
borrar_pruebas

echo; echo "== Lectura (no modifica nada) =="
probar "GET lista"                          200 '['                                          GET /api/lecciones
probar "GET id inexistente"                 404 '"codigo":"RECURSO_NO_ENCONTRADO"'           GET /api/lecciones/12345
probar "GET id no numérico"                 400 '"codigo":"PARAMETRO_INVALIDO"'              GET /api/lecciones/abc
probar "GET id 0"                           400 '"codigo":"PARAMETRO_INVALIDO"'              GET /api/lecciones/0

echo; echo "== POST: crear lecciones con imágenes =="
IMGS="[$(imagen img1.png image/png "$PNG"),$(imagen img2.gif image/gif "$GIF")]"
probar "POST lección A con 2 imágenes"      201 "\"nombre\":\"${PREFIJO}A\""                 POST /api/lecciones "$(lec ${PREFIJO}A COMPILAR_EXITOSO "$IMGS")"
ID_A=$(id_de_la_respuesta)
if grep -qi "^location: $BASE/api/lecciones/$ID_A" "$CABECERAS"; then   # las cabeceras siguen siendo las del POST
  verificar "   el POST devolvió Location: $BASE/api/lecciones/$ID_A" 0
else
  verificar "   el POST debía devolver Location: $BASE/api/lecciones/$ID_A" 1
fi
probar "GET detalle: la imagen PNG vuelve idéntica" 200 "\"contenidoBase64\":\"$PNG\""       GET /api/lecciones/$ID_A
probar "GET detalle: la imagen GIF vuelve idéntica" 200 "\"contenidoBase64\":\"$GIF\""       GET /api/lecciones/$ID_A
probar "GET detalle trae el Markdown y el objetivo" 200 '"objetivo":"COMPILAR_EXITOSO"'      GET /api/lecciones/$ID_A
FECHA_A=$(campo_de_la_respuesta fechaImportacion)
probar "POST lección B sin imágenes (campo omitido)" 201 '"imagenes":[]'                      POST /api/lecciones "$(lec ${PREFIJO}B EJECUTAR_CON_ERROR)"
ID_B=$(id_de_la_respuesta)
probar "POST con Markdown vacío es válido"  201 '"contenidoMarkdown":""'                     POST /api/lecciones "{\"nombre\":\"${PREFIJO}vacia\",\"contenidoMarkdown\":\"\",\"objetivo\":\"COMPILAR_CON_ERROR\"}"
NO_CONTIENE='"contenidoMarkdown"' \
probar "GET lista es liviano: sin Markdown ni imágenes" 200 "\"nombre\":\"${PREFIJO}A\""      GET /api/lecciones
NO_CONTIENE='contenidoBase64' \
probar "GET lista no trae el contenido de las imágenes" 200 "${PREFIJO}B"                      GET /api/lecciones

echo; echo "== Duplicados: 409 LECCION_DUPLICADA (no sobrescribe) =="
probar "POST mismo nombre"                  409 '"codigo":"LECCION_DUPLICADA"'               POST /api/lecciones "$(lec ${PREFIJO}A COMPILAR_EXITOSO)"
probar "POST mismo nombre en mayúsculas"    409 '"codigo":"LECCION_DUPLICADA"'               POST /api/lecciones "$(lec ZZ-PRUEBA-API-a COMPILAR_EXITOSO)"
probar "POST creó 'Lección' con acento (base para la prueba siguiente)" 201 '"id":'           POST /api/lecciones "$(lec ${PREFIJO}Lección COMPILAR_EXITOSO)"
probar "POST mismo nombre sin acento (la colación ignora acentos)" 409 '"codigo":"LECCION_DUPLICADA"' POST /api/lecciones "$(lec ${PREFIJO}LECCION COMPILAR_EXITOSO)"
probar "el duplicado no modificó la lección original" 200 "\"contenidoBase64\":\"$PNG\""      GET /api/lecciones/$ID_A

echo; echo "== POST: validaciones =="
probar "objetivo inválido"                  400 '"campo":"objetivo"'                         POST /api/lecciones "$(lec ${PREFIJO}v1 COMPILAR_MAL)"
probar "sin nombre"                         400 '"campo":"nombre"'                           POST /api/lecciones '{"contenidoMarkdown":"x","objetivo":"COMPILAR_EXITOSO"}'
probar "nombre en blanco"                   400 'no puede estar en blanco'                   POST /api/lecciones "$(lec '   ' COMPILAR_EXITOSO)"
probar "nombre de 256 caracteres"           400 'como máximo 255'                            POST /api/lecciones "$(lec "$(printf 'n%.0s' $(seq 1 256))" COMPILAR_EXITOSO)"
probar "sin contenidoMarkdown"              400 '"campo":"contenidoMarkdown"'                POST /api/lecciones '{"nombre":"x","objetivo":"COMPILAR_EXITOSO"}'
probar "propiedad desconocida"              400 '"codigo":"CUERPO_INVALIDO"'                 POST /api/lecciones '{"nombre":"x","contenidoMarkdown":"","objetivo":"COMPILAR_EXITOSO","otro":1}'
probar "propiedad desconocida dentro de una imagen" 400 '"codigo":"CUERPO_INVALIDO"'         POST /api/lecciones "$(lec ${PREFIJO}v2 COMPILAR_EXITOSO '[{"nombre":"a.png","mimeType":"image/png","contenidoBase64":"AAAA","extra":1}]')"
probar "JSON malformado"                    400 '"codigo":"CUERPO_INVALIDO"'                 POST /api/lecciones '{'
probar "imagenes: null"                     400 '"campo":"imagenes"'                         POST /api/lecciones '{"nombre":"x","contenidoMarkdown":"","objetivo":"COMPILAR_EXITOSO","imagenes":null}'
probar "mimeType no permitido (svg)"        400 '"campo":"imagenes[0].mimeType"'             POST /api/lecciones "$(lec ${PREFIJO}v3 COMPILAR_EXITOSO "[$(imagen a.svg image/svg+xml "$PNG")]")"
probar "base64 inválido"                    400 '"campo":"imagenes[0].contenidoBase64"'      POST /api/lecciones "$(lec ${PREFIJO}v4 COMPILAR_EXITOSO "[$(imagen a.png image/png '***no-es-base64***')]")"
probar "nombre de imagen con '/'"           400 '"campo":"imagenes[0].nombre"'               POST /api/lecciones "$(lec ${PREFIJO}v5 COMPILAR_EXITOSO "[$(imagen '../x.png' image/png "$PNG")]")"
probar "nombres de imagen equivalentes (A.png / a.PNG)" 400 '"campo":"imagenes[1].nombre"'   POST /api/lecciones "$(lec ${PREFIJO}v6 COMPILAR_EXITOSO "[$(imagen A.png image/png "$PNG"),$(imagen a.PNG image/png "$PNG")]")"
VEINTIUNA=""; for i in $(seq 1 21); do VEINTIUNA="$VEINTIUNA$(imagen i$i.png image/png "$PNG"),"; done
probar "21 imágenes (el máximo es 20)"      400 'como máximo 20'                             POST /api/lecciones "$(lec ${PREFIJO}v7 COMPILAR_EXITOSO "[${VEINTIUNA%,}]")"
VEINTE=""; for i in $(seq 1 20); do VEINTE="$VEINTE$(imagen i$i.png image/png "$PNG"),"; done
probar "20 imágenes es válido"              201 '"id":'                                      POST /api/lecciones "$(lec ${PREFIJO}v8 COMPILAR_EXITOSO "[${VEINTE%,}]")"
CODIGO=$(curl -s --cacert "$CERT" -X POST -o "$CUERPO" -w '%{http_code}' -d "$(lec x COMPILAR_EXITOSO)" "$BASE/api/lecciones")
if [ "$CODIGO" = 415 ]; then verificar "POST sin Content-Type: application/json -> 415" 0; else verificar "POST sin Content-Type -> esperaba 415, obtuve $CODIGO" 1; fi

echo; echo "== Imagen grande: integridad del binario de ida y vuelta (~1 MB) =="
GRANDE=$(head -c 786432 /dev/urandom | base64 | tr -d '\n')
printf '{"nombre":"%sgrande","contenidoMarkdown":"x","objetivo":"COMPILAR_EXITOSO","imagenes":[{"nombre":"g.png","mimeType":"image/png","contenidoBase64":"%s"}]}' "$PREFIJO" "$GRANDE" > "$TMP"
probar "POST con una imagen de ~1 MB"       201 '"id":'                                      POST /api/lecciones "@$TMP"
ID_G=$(id_de_la_respuesta)
curl -s --cacert "$CERT" -o "$CUERPO" "$BASE/api/lecciones/$ID_G"
DEVUELTO=$(grep -o '"contenidoBase64":"[^"]*"' "$CUERPO" | head -1 | cut -d'"' -f4)
if [ "$DEVUELTO" = "$GRANDE" ]; then verificar "la imagen de ~1 MB vuelve byte por byte idéntica" 0; else verificar "la imagen de ~1 MB NO volvió idéntica (${#DEVUELTO} vs ${#GRANDE} caracteres)" 1; fi
SOBRE=$(head -c 5300000 /dev/urandom | base64 | tr -d '\n')
printf '{"nombre":"%ssobre","contenidoMarkdown":"x","objetivo":"COMPILAR_EXITOSO","imagenes":[{"nombre":"s.png","mimeType":"image/png","contenidoBase64":"%s"}]}' "$PREFIJO" "$SOBRE" > "$TMP"
probar "imagen por encima del límite (> 7.000.000 caracteres)" 400 'como máximo 7000000'      POST /api/lecciones "@$TMP"

echo; echo "== PUT: reemplazo completo =="
probar "PUT: misma imagen con otro contenido (mismo nombre)" 200 "\"contenidoBase64\":\"$GIF\"" PUT /api/lecciones/$ID_A "$(lec ${PREFIJO}A EJECUTAR_EXITOSO "[$(imagen img1.png image/gif "$GIF")]")"
NO_CONTIENE="$PNG" \
probar "   la imagen vieja y la GIF extra ya no están" 200 '"objetivo":"EJECUTAR_EXITOSO"'      GET /api/lecciones/$ID_A
probar "   el detalle muestra solo 1 imagen"          200 '"nombre":"img1.png"'                GET /api/lecciones/$ID_A
NO_CONTIENE='img2.gif' \
probar "   y la imagen img2.gif desapareció"          200 '"nombre":"img1.png"'                GET /api/lecciones/$ID_A
probar "PUT no cambia fechaImportacion"     200 "\"fechaImportacion\":\"$FECHA_A\""          GET /api/lecciones/$ID_A
probar "PUT con 2 imágenes nuevas"          200 '"nombre":"nueva2.png"'                      PUT /api/lecciones/$ID_A "$(lec ${PREFIJO}A COMPILAR_EXITOSO "[$(imagen nueva1.png image/png "$PNG"),$(imagen nueva2.png image/png "$PNG")]")"
probar "PUT con imagenes: [] las elimina"   200 '"imagenes":[]'                              PUT /api/lecciones/$ID_A "$(lec ${PREFIJO}A COMPILAR_EXITOSO '[]')"
probar "   y el detalle confirma que no hay imágenes" 200 '"imagenes":[]'                    GET /api/lecciones/$ID_A
probar "PUT con su propio nombre no es conflicto" 200 "\"nombre\":\"${PREFIJO}A\""           PUT /api/lecciones/$ID_A "$(lec ${PREFIJO}A COMPILAR_EXITOSO)"
probar "PUT con el nombre de OTRA lección"  409 '"codigo":"LECCION_DUPLICADA"'               PUT /api/lecciones/$ID_A "$(lec ${PREFIJO}B COMPILAR_EXITOSO)"
probar "   el 409 no modificó la lección"   200 "\"nombre\":\"${PREFIJO}A\""                 GET /api/lecciones/$ID_A
probar "PUT sin el campo imagenes"          400 'envíe []'                                   PUT /api/lecciones/$ID_A "{\"nombre\":\"${PREFIJO}A\",\"contenidoMarkdown\":\"x\",\"objetivo\":\"COMPILAR_EXITOSO\"}"
probar "PUT con imágenes de nombre repetido" 400 '"campo":"imagenes[1].nombre"'              PUT /api/lecciones/$ID_A "$(lec ${PREFIJO}A COMPILAR_EXITOSO "[$(imagen a.png image/png "$PNG"),$(imagen A.PNG image/png "$PNG")]")"
probar "PUT sobre un id inexistente (no crea)" 404 '"codigo":"RECURSO_NO_ENCONTRADO"'        PUT /api/lecciones/12345 "$(lec ${PREFIJO}fantasma COMPILAR_EXITOSO)"
probar "   y no creó nada"                  404 '"codigo":"RECURSO_NO_ENCONTRADO"'           GET /api/lecciones/12345
probar "PUT id 0"                           400 '"codigo":"PARAMETRO_INVALIDO"'              PUT /api/lecciones/0 "$(lec ${PREFIJO}x COMPILAR_EXITOSO)"
probar "PUT id no numérico"                 400 '"codigo":"PARAMETRO_INVALIDO"'              PUT /api/lecciones/abc "$(lec ${PREFIJO}x COMPILAR_EXITOSO)"

echo; echo "== DELETE =="
probar "DELETE lección A"                   204 ''                                           DELETE /api/lecciones/$ID_A
probar "GET lección A ya no existe"         404 '"codigo":"RECURSO_NO_ENCONTRADO"'           GET /api/lecciones/$ID_A
probar "DELETE otra vez"                    404 '"codigo":"RECURSO_NO_ENCONTRADO"'           DELETE /api/lecciones/$ID_A
probar "DELETE id 0"                        400 '"codigo":"PARAMETRO_INVALIDO"'              DELETE /api/lecciones/0
probar "DELETE lección B"                   204 ''                                           DELETE /api/lecciones/$ID_B

echo; echo "== Limpieza final =="
borrar_pruebas
NO_CONTIENE="$PREFIJO" \
probar "no queda ninguna lección de prueba" 200 '['                                          GET /api/lecciones

echo; echo "Resultado: $OK correctas, $FALLA con fallo."
[ "$FALLA" = 0 ]

{{/*
Standard labels applied to all resources in this chart.
*/}}
{{- define "cicd-system.labels" -}}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Resolves the PostgreSQL hostname.
Returns "postgres" when the in-cluster StatefulSet is enabled,
otherwise falls back to the externally-supplied host value.
*/}}
{{- define "cicd-system.dbHost" -}}
{{- if .Values.postgres.enabled -}}
postgres
{{- else -}}
{{ .Values.postgres.host }}
{{- end -}}
{{- end }}

{{/*
Resolves the RabbitMQ hostname.
Returns "rabbitmq" when the in-cluster StatefulSet is enabled,
otherwise falls back to the externally-supplied host value.
*/}}
{{- define "cicd-system.rabbitmqHost" -}}
{{- if .Values.rabbitmq.enabled -}}
rabbitmq
{{- else -}}
{{ .Values.rabbitmq.host }}
{{- end -}}
{{- end }}

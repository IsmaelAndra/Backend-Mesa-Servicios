package com.mesaayuda.dto;
import java.util.UUID;

/**
 * Resultado compacto de un workflow en la búsqueda global.
 *
 * <p>El grupo de workflows solo se sirve a SUPER_ADMIN, el mismo rol que gobierna
 * {@code /admin/workflows}: programar una automatización es escribir código con
 * permisos ajenos, así que ni siquiera el nombre de un workflow se lista a quien
 * no pueda administrarlo. Solo se expone lo mínimo para identificar el proceso
 * y abrirlo en el constructor.
 *
 * @param idWorkflow      para abrir el workflow
 * @param nameWorkflow    nombre del proceso
 * @param statusWorkflow  BORRADOR, ACTIVO, INACTIVO o ERROR
 */
public record SearchWorkflowItem(UUID idWorkflow, String nameWorkflow, String statusWorkflow) {
}
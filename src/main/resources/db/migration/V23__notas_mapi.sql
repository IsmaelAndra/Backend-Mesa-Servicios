-- Las notas internas que dejó la automatización de clasificación cuando la
-- asistente se llamaba NEXA empiezan por "NEXA: ...". Se cambian a "MAPI: ..."
-- para que en las conversaciones solo aparezca un nombre. Solo notas internas
-- que empiezan así (las que escribe la automatización); lo que haya escrito una
-- persona no se toca. El rastro de auditoría (ticket_audit) es de solo
-- inserción y conserva el texto original.
UPDATE answer
   SET message_answer = regexp_replace(message_answer, '^(\s*(<p>)?\s*)NEXA:', '\1MAPI:')
 WHERE internal_answer = TRUE
   AND message_answer ~ '^\s*(<p>)?\s*NEXA:';

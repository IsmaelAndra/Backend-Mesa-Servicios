-- La asistente de IA deja de llamarse NEXA y pasa a llamarse MAPI. Su nombre
-- vive en las reglas de la institución ("Eres NEXA, la asistente..."), que es
-- lo que la IA usa para presentarse. Solo se cambia la palabra completa; el
-- resto del texto que escribió el administrador no se toca.
UPDATE ai_config
   SET rules_text = regexp_replace(rules_text, '\mNEXA\M', 'MAPI', 'g')
 WHERE rules_text ~ '\mNEXA\M';

-- La automatización de fábrica se renombra al arrancar (WorkflowsDeFabrica),
-- que además publica una versión con los textos nuevos sin tocar su lógica.

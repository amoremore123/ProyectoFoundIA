export default function ChipTipo({ tipo }) {
  const esPerdido = tipo === 'PERDIDO';
  return (
    <span className={`chip ${esPerdido ? 'chip-perdido' : 'chip-encontrado'}`}>
      {esPerdido ? 'Perdido' : 'Encontrado'}
    </span>
  );
}

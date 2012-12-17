<h2>Hibersap Plugin for Forge</h2>
This is a fork of the hibersap forge plugin: https://github.com/forge/plugin-hibersap<br><br>
Main Change: Introduced <a href="http://projectlombok.org">Lombok</a> to reduce boiler plate code.
Instead of big java files with generated beans, lombok annotations are used to generate:<br>
<ul>
<li>getters and setters</li>
<li>Default Constructor (so it can be (de)serialized in xml / json without much effort</li>
<li>toString</li>
<li>equal, canEqual, hashCode</li>
</ul><br>
generated items are now serializable, which is needed for <a href="https://github.com/bjoben/camel-hibersap">camel-hibersap-plugin</a>
<h2>Example<h2>
An incomplete example of the generated classes:<br>
<pre>
@NoArgsConstructor
@ToString
@Data
public @Bapi("BAPI_MATERIAL_SAVEDATA")
class BapiMaterialSavedata implements Serializable
{

   @Import
   @Parameter(value = "PLANTDATA", type = ParameterType.STRUCTURE)
   private Plantdata _plantdata;
   
   @Export
   @Parameter(value = "RETURN", type = ParameterType.STRUCTURE)
   private Return _return;
   
   @Table
   @Parameter("UNITSOFMEASURE")
   private List<Unitsofmeasure> _unitsofmeasure;



   public BapiMaterialSavedata(final Plantdata plantdata)
   {
      this._plantdata = plantdata;
   }
}

</pre>
So there will still be a constructor that inits all import-parameter but there is also a no-arg-constructor. 


This plugin is under LGPL licence.
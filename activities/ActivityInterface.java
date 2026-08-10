
@ActivityInterface
public interface CreateTripInterface {
    @ActivityMethod
    public void createTrip(MakeTripDto makeTripDto);
    @ActivityMethod
    public void bookHotel(MakeTripDto makeTripDto);
    @ActivityMethod
    public void bookTransportation(MakeTripDto makeTripDto);
    @ActivityMethod
    void cancelTrip(MakeTripDto makeTripDto);
    @ActivityMethod
    void cancelHotel(MakeTripDto makeTripDto);
    @ActivityMethod
    void cancelTransportation(MakeTripDto makeTripDto);
}
